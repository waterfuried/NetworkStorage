import prefs.*;

import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.input.*;

import java.io.*;

import java.net.URL;

import java.nio.file.*;

import java.util.*;
import java.util.stream.*;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

public class PanelController implements Initializable {
    @FXML private Label nameLabel;
    @FXML private ComboBox<String> disks;
    @FXML private TextField curPath;
    @FXML private TableView<FileInfo> filesTable;
    @FXML private Button btnLevelUp, btnGoBack;

    private Stack<String> prevPath;
    private int curDiskIdx;
    private boolean serverMode = false, editing = false;
    private List<FileInfo> serverFolder;

    private int savedIdx;

    private NeStController parentController;

    void setController (NeStController controller) {
        parentController = controller;
    }

    void setServerFolder(List<FileInfo> serverFolder) { this.serverFolder = serverFolder; }

    void updateFreeSpace(long freeSpace) {
        nameLabel.setText("Server files ("+(freeSpace/1000/1000)+"M free)");
    }

    void setServerMode() {
        serverMode = true;
        disks.setVisible(false);
        btnLevelUp.setDisable(true);
        btnGoBack.setDisable(true);
        setCurPath("");
        if (prevPath != null) prevPath.clear();
        btnGoBack.setDisable(true);
    }

    void setLocalMode(String path) {
        serverMode = false;
        nameLabel.setText("Client files");
        disks.setVisible(true);
        btnLevelUp.setDisable(Paths.get(path).getParent() == null);
        btnGoBack.setDisable(true);
        setCurPath(path);
        if (prevPath != null) prevPath.clear();
        btnGoBack.setDisable(true);
    }

    boolean isServerMode() { return serverMode; }

    void updateFilesList() {
        updateFilesList(Paths.get(getCurPath()));
    }

    void updateFilesList(Path path) {
        try {
            refreshCurPath(path.normalize().toAbsolutePath().toString());
            if (filesTable.getItems() != null) filesTable.getItems().clear();
            try (Stream<Path> ps = Files.list(path)) {
                filesTable.getItems().addAll(ps.map(FileInfo::new).collect(Collectors.toList()));
            }
            filesTable.sort();
        } catch (IOException ex) {
            Messages.displayError(ERR_FOLDER_ACCESS_DENIED, "");
            ex.printStackTrace();
        }
    }

    void updateServerFilesList(String path) {
        // отображение дубликатов в столбцах -- всего лишь один из частых глюков с TableView,
        // другой заключается в невозможности доступа к значениям ячеек отображаемых столбцов
        refreshCurPath(path);
        if (filesTable.getItems() != null) filesTable.getItems().clear();
        if (serverFolder != null)
            try {
                filesTable.getItems().setAll(serverFolder);
                filesTable.sort();
            } catch (Exception ex) {
                Messages.displayError(ERR_FOLDER_ACCESS_DENIED, "");
                ex.printStackTrace();
            }
    }

    @FXML void cmbxChangeDisk(/*ActionEvent ev*/) {
        if (disks.getSelectionModel().getSelectedIndex() == curDiskIdx) return;
        pushCurrentPath();
        updateFilesList(Paths.get(disks.getSelectionModel().getSelectedItem()));
        curDiskIdx = disks.getSelectionModel().getSelectedIndex();
        btnLevelUp.setDisable(true);
        btnGoBack.setDisable(prevPath.isEmpty());
    }

    void pushCurrentPath() {
        if (prevPath == null) prevPath = new Stack<>();
        prevPath.push(getCurPath());
        if (btnGoBack.isDisable()) btnGoBack.setDisable(false);
    }

    String getParentPath(String path) {
        int i = path.lastIndexOf(File.separatorChar);
        return i < 0 ? "" : path.substring(0, i);
    }

    @FXML void btnLevelUpAction(/*ActionEvent ev*/) {
        pushCurrentPath();
        boolean atRoot;
        if (serverMode) {
            String parentPath = getParentPath(getCurPath());
            atRoot = parentPath.length() == 0;
            parentController.requestFiles(parentPath);
        } else {
            Path parentPath = Paths.get(getCurPath()).getParent();
            atRoot = Paths.get(parentPath.toString()).getParent() == null;
            updateFilesList(parentPath);
        }
        btnLevelUp.setDisable(atRoot);
    }

    @FXML void btnGoBackAction(/*ActionEvent ev*/) {
        boolean atRoot;
        if (serverMode) {
            String prev = prevPath.pop();
            atRoot = prev.length() == 0;
            parentController.requestFiles(prev);
        } else {
            Path prev = Paths.get(prevPath.pop());
            atRoot = Paths.get(prev.toString()).getParent() == null;
            updateFilesList(prev);
        }
        btnGoBack.setDisable(prevPath.isEmpty());
        btnLevelUp.setDisable(atRoot);
    }

    @FXML void btnRefreshAction(/*ActionEvent actionEvent*/) {
        if (serverMode)
            parentController.requestFiles(getCurPath());
        else
            updateFilesList(Paths.get(getCurPath()));
    }

    String getSelectedFilename() {
        return filesTable.getSelectionModel().getSelectedItem() != null
                ? filesTable.getSelectionModel().getSelectedItem().getFilename()
                : "";
    }

    Long getSelectedFileSize() {
        return filesTable.getSelectionModel().getSelectedItem() != null
                ? filesTable.getSelectionModel().getSelectedItem().getSize()
                : null;
    }

    String getCurPath() { return curPath.getText(); }
    void setCurPath(String path) {
        refreshCurPath(path);
        if (serverMode) updateServerFilesList(path); else updateFilesList(Paths.get(path));
    }

    String getFullSelectedFilename() {
        return getCurPath()+(getCurPath().length() == 0 ? "" : File.separatorChar)+getSelectedFilename();
    }

    TableView<FileInfo> getFilesTable() { return filesTable; }

    int getSavedIdx() { return savedIdx; }

    void setSavedIdx(int savedIdx) { this.savedIdx = savedIdx; }

    void setEditing(boolean editing) { this.editing = editing; }

    int getIndexOf(String entryName) {
        for (int i = 0; i < filesTable.getItems().size(); i++)
            if (filesTable.getItems().get(i).getFilename().equals(entryName)) return i;
        return -1;
    }

    int getIndexOfAnyMatch(String entryName) {
        for (int i = 0; i < filesTable.getItems().size(); i++)
            if (filesTable.getItems().get(i).getFilename().equalsIgnoreCase(entryName)) return i;
        return -1;
    }

    void refreshCurPath(String path) {
        curPath.setText(path);
        if (curPath.getTooltip() == null) {
            if (path.length() > 0) curPath.setTooltip(new Tooltip(path));
        } else {
            if (path.length() == 0)
                curPath.setTooltip(null);
            else
                curPath.getTooltip().setText(path);
        }
    }

    int getCurDisk() { return curDiskIdx; }

    int getSizeColumn() {
        int i = 0;
        while (i < filesTable.getColumns().size() &&
               !filesTable.getColumns().get(i).getText().equals("Size")) i++;
        return i < filesTable.getColumns().size() ? i : -1;
    }

    // методы обработки выбранного элемента списка
    /**
     * активация - нажатие Enter или двойной щелчок<br><br>
     * всвязи с активацией переименования выбранного элемента
     * как по нажатию Enter, так и двойному щелчку (или двум щелчкам),
     * могут происходить коллизии обработки
     */
    void processItem() {
        if (getSelectedFilename().length() == 0) return;
        Path p = Paths.get(getCurPath(), getSelectedFilename());
        if ((long)filesTable.getColumns().get(getSizeColumn())
                  .getCellData(filesTable.getSelectionModel().getSelectedIndex()) < 0L) {
            if (serverMode)
                parentController.requestFiles(p.toString());
            else
                updateFilesList(p);
            pushCurrentPath();
            btnLevelUp.setDisable(false);
        }
    }

    /**
     * удаление (по нажатию Delete):<br><br>
     *    данный метод удаляет файл (папку) только на клиенте
     *    и вызывается не напрямую, а из метода родительского
     *    контроллера tryRemove - пояснение см. в его описании
     */
    void removeItem() {
        try {
            Files.delete(Paths.get(getCurPath(), getSelectedFilename()));
            updateFilesList(Paths.get(getCurPath()));
            filesTable.getSelectionModel().select(savedIdx);
        } catch (IOException ex) {
            ErrorCode errCode = ERR_NO_SUCH_FILE;
            if (ex instanceof DirectoryNotEmptyException) errCode = ERR_NOT_EMPTY;
            Messages.displayError(errCode, ERR_OPERATION_FAILED, COM_REMOVE);
        }
    }

    /**
     * переименование - и только: перемещения не допускаются (TODO),
     * по меньшей мере до реализации возможности переключать
     * любую(!) панель между клиентским компьютером и папкой
     * пользователя на сервере - именно такой подход позволит
     * избежать избыточного анализа вводимых строк с новым именем.
     * <br><br>
     * переименовывает файл (папку) только на клиенте,
     * переименованием на сервере занимается
     * метод родительского контроллера
     * @param curName     текущее имя элемента
     * @param newName     новое имя элемента
     * @return  0 = переименование завершено (с любым исходом),<br>
     *          1 = переименование можно выполнять с заменой совпадений,<br>
     *         -1 = переименование можно выполнять, но наличие совпадений
     *              не может быть установлено
     */
    int renameItem(String curName, String newName, boolean restore) {
        if (newName.equals(curName)) return 0;
        boolean wrongName = newName.length() == 0 || hasInvalidCharacters(newName),
                stayHere = newName.equals("."), levelUp = newName.equals("..");

        if (filesTable.getItems().size() == 1 && !wrongName && !levelUp && !stayHere) {
            if (!serverMode) rename(Paths.get(getCurPath(), curName), newName, false);
            return 1;
        }

        if (restore) {
            int row = filesTable.getSelectionModel().getSelectedIndex();
            FileInfo cfi = filesTable.getItems().get(row);
            cfi.setFilename(curName);
            filesTable.getItems().set(row, cfi);
            filesTable.refresh();
        }

        if (stayHere) return 0;
        if (wrongName || levelUp) {
            // чтобы дождаться факта закрытия диалога (и только после него что-либо сделать),
            // нужно вызывать его непосредственно - не в отдельном потоке обработки графики
            if (newName.length() > 0)
                Messages.displayError(wrongName ? ERR_INVALID_NAME : ERR_REPLACEMENT_NOT_ALLOWED, "");
            return 0;
        }

        // если элемент с новым именем отсутствует в списке,
        // в случае extFS он действительно отсутствует,
        // в случае же NTFS/FAT он либо отсутствует,
        // либо присутствует в единственном экземпляре -
        // какие-либо символы в его имени имеют другой регистр
        // и без попытки изменения его имени средствами ФС
        // невозможно однозначно ответить на вопрос о его существовании
        int newType = checkPresence(newName);
        if (newType < 0) {
            int n = serverMode ? -1 : rename(Paths.get(getFullSelectedFilename()), newName, false);
            if (n < 0) return -1;
            switch (ErrorCode.values()[n]) {
                case ERR_NO_SUCH_FILE:
                    n = getIndexOfAnyMatch(newName);
                    if (n < filesTable.getItems().size())
                        newType = filesTable.getItems().get(n).getSize() < 0L ? 1 : 0;
                    break;
                case ERR_FOLDER_ACCESS_DENIED:
                    Messages.displayError(ERR_FOLDER_ACCESS_DENIED, ERR_OPERATION_FAILED);
                    return 0;
            }
        }
        if (isRenameable(newName, newType)) {
            if (!serverMode) rename(Paths.get(getFullSelectedFilename()), newName, true);
            return 1;
        }
        return 0;
    }

    /**
     * проверка существования элемента в списке;
     * гарантирует только то, что существует (отстутсвует) элемент
     * точно с таким же именем - с учетом регистра символов в нем
     * @param name     имя элемента
     * @return -1, если элемент отсутствует,<br>
     *          0, если элемент является файлом,<br>
     *          1, если элемент является папкой
     */
    int checkPresence(String name) {
        for (FileInfo fi : filesTable.getItems())
            if (fi.getFilename().equals(name))
                return fi.getSize() >= 0 ? 0 : 1;
        return -1;
    }

    boolean isRenameable(String newName, int newType) {
        boolean wrongReplace = false, canRename = false;
        if (newType == 0) { // элемент с новым именем - файл
            wrongReplace = filesTable.getItems()
                    .get(filesTable.getSelectionModel().getSelectedIndex()).getSize() < 0;
            if (!wrongReplace && Messages.confirmReplacement(true, newName))
                canRename = true;
        }
        if (newType == 1) // ...папка
            Messages.displayError(ERR_REPLACEMENT_NOT_ALLOWED, "");
        if (wrongReplace)
            Messages.displayError(ERR_WRONG_REPLACEMENT, "");
        return canRename;
    }

    @FXML void keyboardHandler(KeyEvent ev) {
        // I think it is a kind of bug:
        // any key Event including key presses (like del or enter)
        // while editing text at text editor of main controller's
        // TextInputDialog will be caught here
        if (filesTable.getSelectionModel().getSelectedIndex() < 0) return;
        switch (ev.getCode()) {
            case DELETE: if (!editing) parentController.tryRemove(); break;
            case ENTER: if (editing) editing = false; else processItem();
        }
    }

    @FXML void mouseHandler(MouseEvent ev) {
        if (ev.getClickCount() == 2) processItem();
    }
    /*
        Урок 3. Фреймворк Netty
        1. Взять код с урока и добавить логику навигации по папкам на клиенте и на сервере.
        PathUpRequest
        PathInRequest

        содержимое любой (под)папки на сервере контроллер клиента
        получает через запрос и передает сюда в виде списка,
        по которому содержимое папки отображается в панели сервера;

        при произведенном изменении навигации по папкам диска на сервере
        с работы с диском на работу с полученными списками элементов
        были написаны новые методы и изменен код некоторых имеющихся,
        в частности - методов перехода вверх и вниз по дереву папок,
        что и требовалось в задании
     */
    @Override public void initialize(URL url, ResourceBundle resourceBundle) {
        filesTable.setPlaceholder(new Label("< The folder is empty >"));
        TableColumn<FileInfo, String> fileNameColumn = new TableColumn<>("Name");
        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Size");
        TableColumn<FileInfo, String> fileDateColumn = new TableColumn<>("Date");

        fileNameColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getFilename()));
        fileNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        fileNameColumn.setOnEditStart(ev -> editing = true);
        fileNameColumn.setOnEditCancel(ev -> editing = false);
        fileNameColumn.setOnEditCommit(ev -> {
            editing = true;
            int replace = renameItem(ev.getOldValue(), ev.getNewValue(), true);
            if (replace != 0)
                if (serverMode)
                    parentController.renameEntry(ev.getNewValue(), replace == 1);
                else {
                    updateFilesList(Paths.get(getCurPath()));
                    filesTable.getSelectionModel().select(getIndexOf(ev.getNewValue()));
                    parentController.onPanelUpdated();
                }
        });
        fileSizeColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getSize()));
        fileSizeColumn.setCellFactory(c -> new TableCell<FileInfo, Long>() {
            @Override protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null || empty
                        ? ""
                        : item < 0 ? "[folder]" : String.format("%,d", item));
            }
        });
        fileDateColumn.setCellValueFactory(
                p -> new SimpleStringProperty(p.getValue().getModified().format(dtFmt)));

        fileNameColumn.setPrefWidth(300);
        fileSizeColumn.setPrefWidth(100);
        fileDateColumn.setPrefWidth(200);

        Path startPath = Paths.get(".");
        curDiskIdx = 0;
        boolean found = false;
        disks.getItems().clear();
        for (Path d : FileSystems.getDefault().getRootDirectories()) {
            disks.getItems().add(d.toString());
            if (startPath.normalize().toAbsolutePath().toString().substring(0,3).equals(d.toString()))
                found = true;
            if (!found) curDiskIdx++;
        }
        disks.getSelectionModel().select(curDiskIdx);

        filesTable.getColumns().addAll(fileNameColumn, fileSizeColumn, fileDateColumn);
        filesTable.getSortOrder().add(fileSizeColumn);

        updateFilesList(startPath);
    }
}