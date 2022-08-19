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
    @FXML private MenuItem cmiCopy, cmiMove, cmiRename, cmiRemove;

    private Stack<String> prevPath;
    private int curDiskIdx, clientFS;
    private boolean serverMode = false, editing = false, requesting = false;
    private List<FileInfo> serverFolder;

    private NeStController parentController;

    void setController (NeStController controller) {
        parentController = controller;
    }

    List<FileInfo> getServerFolder() { return serverFolder; }
    void setServerFolder(List<FileInfo> serverFolder) { this.serverFolder = serverFolder; }

    void updateFreeSpace(long freeSpace) {
        nameLabel.setText("Server files ("+(freeSpace/1000/1000)+"M free)");
    }

    private void setMode(String path, boolean local) {
        serverMode = !local;
        if (local) nameLabel.setText("Client files");
        disks.setVisible(local);
        setCurPath(path);
        filesTable.getSelectionModel().clearSelection();
        // метод getParent не обращается к ФС
        btnLevelUp.setDisable(local ? Paths.get(getCurPath()).getParent() == null : path.length() == 0);
        btnGoBack.setDisable(true);
        if (prevPath != null) prevPath.clear();
    }

    void setServerMode() { setServerMode(""); }
    void setServerMode(String path) { setMode(path, false); }
    void setLocalMode(String path) { setMode(path, true); }

    boolean atServerMode() { return serverMode; }

    void updateFilesList() { updateFilesList(getCurPath()); }

    void updateFilesList(String path) {
        // отображение дубликатов в столбцах -- всего лишь один из частых глюков с TableView,
        // другой заключается в невозможности доступа к значениям ячеек отображаемых столбцов
        try {
            int i = -1;
            if (filesTable.getItems() != null) {
                i = filesTable.getSelectionModel().getSelectedIndex();
                filesTable.getItems().clear();
            }
            if (serverMode) {
                refreshCurPath(path);
                if (serverFolder != null) filesTable.getItems().setAll(serverFolder);
                requesting = false;
            } else {
                Path p = Paths.get(path);
                refreshCurPath(p.normalize().toAbsolutePath().toString());
                try (Stream<Path> ps = Files.list(p)) {
                    filesTable.getItems().addAll(ps.map(FileInfo::new).collect(Collectors.toList()));
                }
            }
            filesTable.sort();
            filesTable.getSelectionModel().select(i);
        } catch (IOException ex) {
            Messages.displayError(ERR_FOLDER_ACCESS_DENIED, "");
            ex.printStackTrace();
        }
    }

    @FXML void cmbxChangeDisk() {
        if (disks.getSelectionModel().getSelectedIndex() == curDiskIdx) return;
        pushCurrentPath();
        updateFilesList(disks.getSelectionModel().getSelectedItem());
        curDiskIdx = disks.getSelectionModel().getSelectedIndex();
        btnLevelUp.setDisable(true);
        btnGoBack.setDisable(prevPath.isEmpty());
        clientFS = getFSType(Paths.get(getCurPath()));
    }

    void pushCurrentPath() {
        if (prevPath == null) prevPath = new Stack<>();
        prevPath.push(getCurPath());
        if (btnGoBack.isDisable()) btnGoBack.setDisable(false);
    }

    @FXML void btnLevelUpAction() {
        pushCurrentPath();
        boolean atRoot;
        if (serverMode) {
            int i = getCurPath().lastIndexOf(File.separatorChar);
            atRoot = i < 0;
            String parentPath = atRoot ? "" : getCurPath().substring(0, i);
            requesting = true;
            parentController.requestFiles(parentPath);
        } else {
            Path parentPath = Paths.get(getCurPath()).getParent();
            atRoot = Paths.get(parentPath.toString()).getParent() == null;
            updateFilesList(parentPath.toString());
        }
        btnLevelUp.setDisable(atRoot);
    }

    @FXML void btnGoBackAction() {
        boolean atRoot;
        if (serverMode) {
            String prev = prevPath.pop();
            atRoot = prev.length() == 0;
            requesting = true;
            parentController.requestFiles(prev);
        } else {
            Path prev = Paths.get(prevPath.pop());
            atRoot = Paths.get(prev.toString()).getParent() == null;
            updateFilesList(prev.toString());
        }
        btnGoBack.setDisable(prevPath.isEmpty());
        btnLevelUp.setDisable(atRoot);
    }

    @FXML void btnRefreshAction() {
        if (serverMode) {
            requesting = true;
            parentController.requestFiles(getCurPath());
        } else
            updateFilesList();
    }

    String getSelectedFilename() {
        return filesTable.getSelectionModel().getSelectedItem().getFilename();
    }

    long getSelectedFileSize() {
        return filesTable.getSelectionModel().getSelectedItem().getSize();
    }

    long getSelectedFileTime() {
        return filesTable.getSelectionModel().getSelectedItem().getModifiedAsLong();
    }

    boolean isFileSelected() {
        return filesTable.getSelectionModel().getSelectedItem().getSize() >= 0L;
    }

    boolean isFile(int idx) { return filesTable.getItems().get(idx).getSize() >= 0L; }

    String getCurPath() { return curPath.getText(); }
    void setCurPath(String path) {
        refreshCurPath(path);
        updateFilesList(path);
    }

    String getFullSelectedFilename() {
        return getCurPath()+(getCurPath().length() == 0 ? "" : File.separatorChar)+getSelectedFilename();
    }

    TableView<FileInfo> getFilesTable() { return filesTable; }

    void setEditing(boolean editing) { this.editing = editing; }

    public boolean hasRequest() { return requesting; }
    public void createRequest() { requesting = true; }

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

    int getClientFS() { return serverMode ? FS_UNK : clientFS; }

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

    // методы обработки выбранного элемента списка
    /**
     * активация - нажатие Enter или двойной щелчок<br><br>
     * всвязи с активацией переименования выбранного элемента
     * как по нажатию Enter, так и двойному щелчку (или двум щелчкам),
     * могут происходить коллизии обработки
     */
    void processItem() {
        if (getSelectedFilename().length() == 0) return;
        if (getSelectedFileSize() < 0L) {
            if (serverMode) {
                requesting = true;
                parentController.requestFiles(getFullSelectedFilename());
            } else {
                updateFilesList(getFullSelectedFilename());
                filesTable.getSelectionModel().clearSelection();
            }
            pushCurrentPath();
            btnLevelUp.setDisable(false);
        }
    }

    /**
     * проверить возможность переименования файла (папки),
     * и при ее наличии выполнить переименование, но только на клиенте -
     * переименованием на сервере занимается метод родительского контроллера
     * @param curName   текущее имя элемента
     * @param newName   новое имя элемента
     * @param restore   восстановить текущее имя, если оно неприемлемо -
     *                  используется при правке имени непосредственно
     *                  в строке таблицы со списком файлов
     * @return 0 = переименование завершено (с любым исходом),<br>
     *         1 = переименование можно выполнять с заменой совпадений
     */
    boolean renameItem(String curName, String newName, boolean restore) {
        if (newName.equals(curName)) return false;
        boolean wrongName = newName.length() == 0 || hasInvalidCharacters(newName),
                stayHere = newName.equals("."), levelUp = newName.equals("..");

        if (filesTable.getItems().size() == 1 && !wrongName && !levelUp && !stayHere) {
            if (!serverMode) rename(Paths.get(getCurPath(), curName), newName);
            return true;
        }

        if (restore) {
            int row = filesTable.getSelectionModel().getSelectedIndex();
            FileInfo cfi = filesTable.getItems().get(row);
            cfi.setFilename(curName);
            filesTable.getItems().set(row, cfi);
            filesTable.refresh();
        }

        if (stayHere) return false;
        if (wrongName || levelUp) {
            // чтобы дождаться факта закрытия диалога (и только после него что-либо сделать),
            // нужно вызывать его непосредственно - не в отдельном потоке обработки графики
            if (newName.length() > 0)
                Messages.displayError(wrongName ? ERR_INVALID_NAME : ERR_REPLACEMENT_NOT_ALLOWED, "");
            return false;
        }

        int FSType = serverMode ? parentController.getServerFS() : clientFS,
            newIdx = FSType == FS_EXTFS ? getIndexOf(newName) : getIndexOfAnyMatch(newName);
        boolean canRename = newIdx < 0 || (FSType == FS_NTFS && curName.equalsIgnoreCase(newName));
        if (!canRename)
            if (isFile(newIdx) && isFileSelected()) {
                if (Messages.confirmReplacement(newName)) canRename = true;
            } else
                Messages.displayWrongReplacement(COM_RENAME, isFile(newIdx));

        if (canRename) {
            if (!serverMode) {
                rename(Paths.get(getFullSelectedFilename()), newName);
                updateFilesList();
            }
            return true;
        }
        return false;
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

    // обновить названия и доступность элементов контекстного меню
    void refreshFileCmd(String cmdCopy, String cmdMove) {
        cmiCopy.setText(cmdCopy);
        cmiMove.setText(cmdMove);
    }

    void refreshFileOps(boolean cantCopy, boolean cantMove) {
        cmiCopy.setDisable(cantCopy);
        cmiMove.setDisable(cantMove);
        boolean empty = filesTable.getItems().size() == 0;
        cmiRename.setDisable(empty);
        cmiRemove.setDisable(empty);
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
            if (renameItem(ev.getOldValue(), ev.getNewValue(), true))
                if (serverMode) {
                    requesting = true;
                    parentController.renameEntry(ev.getNewValue());
                } else {
                    updateFilesList();
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

        updateFilesList(".");
        clientFS = getFSType(Paths.get(getCurPath()));
        boolean empty = filesTable.getItems().size() == 0;
        cmiCopy.setText(capitalize(COM_COPY));
        cmiCopy.setDisable(empty);
        cmiCopy.setOnAction(ev -> parentController.copyOrUpload());
        cmiMove.setText(capitalize(COM_MOVE));
        cmiMove.setDisable(empty);
        cmiMove.setOnAction(ev -> parentController.moveOrDownload());
        cmiRename.setText(capitalize(COM_RENAME));
        cmiRename.setDisable(empty);
        cmiRename.setOnAction(ev -> parentController.tryRename());
        cmiRemove.setText(capitalize(COM_REMOVE));
        cmiRemove.setDisable(empty);
        cmiRemove.setOnAction(ev -> parentController.tryRemove());
    }
}