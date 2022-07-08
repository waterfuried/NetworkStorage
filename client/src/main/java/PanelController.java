import prefs.*;

import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.input.*;

import java.io.*;

import java.net.URL;

import java.nio.file.*;

import java.util.*;
import java.util.stream.*;

public class PanelController implements Initializable {
    @FXML Label nameLabel;
    @FXML ComboBox<String> disks;
    @FXML TextField curPath;
    @FXML TableView<FileInfo> filesTable;
    @FXML Button btnLevelUp, btnGoBack, btnRefresh;

    private Stack<String> prevPath;
    private int curDiskIdx;
    private boolean serverMode = false;
    private List<FileInfo> serverFolder;

    private NeStController parentController;

    public void setController (NeStController controller) {
        parentController = controller;
    }

    public void setServerFolder(List<FileInfo> serverFolder) { this.serverFolder = serverFolder; }

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

    void updateFilesList(Path path) {
        try {
            refreshCurPath(path.normalize().toAbsolutePath().toString());
            if (filesTable.getItems() != null) filesTable.getItems().clear();
            try (Stream<Path> ps = Files.list(path)) {
                filesTable.getItems().addAll(ps.map(FileInfo::new).collect(Collectors.toList()));
            }
            filesTable.sort();
        } catch (IOException ex) {
            Messages.displayError("Failed to update list of files.", "");
        }
    }

    void updateServerFilesList(String path) {
        // отображение дубликатов в столбцах -- всего лишь один из частых глюков с TableView,
        // другой заключается в невозможности доступа к значениям ячеек отображаемых столбцов
        refreshCurPath(path);
        if (filesTable.getItems() != null) filesTable.getItems().clear();
        if (serverFolder != null)
            try {
                filesTable.getItems().addAll(serverFolder); // иногда стоит использовать setAll
                filesTable.sort();
            } catch (Exception ex) {
                Messages.displayErrorFX("Failed to update list of files.", "");
            }
    }

    @FXML void cmbxChangeDisk(/*ActionEvent ev*/) {
        if (disks.getSelectionModel().getSelectedIndex() != curDiskIdx) {
            pushCurrentPath();
            updateFilesList(Paths.get(disks.getSelectionModel().getSelectedItem()));
            curDiskIdx = disks.getSelectionModel().getSelectedIndex();
            btnLevelUp.setDisable(true);
            btnGoBack.setDisable(prevPath.isEmpty());
        }
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
        return filesTable.isFocused() && filesTable.getSelectionModel().getSelectedItem() != null
                ? filesTable.getSelectionModel().getSelectedItem().getFilename()
                : "";
    }

    String getCurPath() { return curPath.getText(); }
    void setCurPath(String path) {
        refreshCurPath(path);
        if (serverMode) updateServerFilesList(path); else updateFilesList(Paths.get(path));
    }

    String getFullSelectedFilename() {
        return getCurPath()+(getCurPath().length() == 0 ? "" : File.separatorChar)+getSelectedFilename();
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

    /**
     * методы обработки выбранного элемента списка
     *
     * 1. активация - нажатие Enter или двойной щелчок
     */
    void processItem() {
        if (filesTable.getSelectionModel().getSelectedItem() != null) {
            Path p = Paths.get(getCurPath(), filesTable.getSelectionModel().getSelectedItem().getFilename());
            boolean isFolder = false;
            if (serverMode) {
                int i = getSizeColumn();
                if (i >= 0) {
                    TableColumn<FileInfo, Long> fileSizeColumn =
                            (TableColumn<FileInfo, Long>)filesTable.getColumns().get(i);
                    isFolder = fileSizeColumn.getCellData(
                            filesTable.getSelectionModel().getSelectedIndex()).intValue() < 0;
                    if (isFolder)
                        parentController.requestFiles(p.toString());
                }
            } else {
                isFolder = Files.isDirectory(p);
                if (isFolder) updateFilesList(p);
            }
            if (isFolder) {
                pushCurrentPath();
                btnLevelUp.setDisable(false);
            }
        }
    }

    /**
     * 2. удаление (по нажатию Delete):
     *    данный метод удаляет файл (папку) только на клиенте
     *    и вызывается не напрямую, а из метода родительского
     *    контроллера tryRemove - пояснение см. в его описании
     */
    void removeItem() {
        try {
            Files.delete(Paths.get(getCurPath(), getSelectedFilename()));
            Messages.displayInfo(getSelectedFilename() + " removed successfully", "Removal completed");
            updateFilesList(Paths.get(getCurPath()));
        } catch (IOException ex) {
            Prefs.ErrorCode errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE;
            if (ex instanceof DirectoryNotEmptyException) errCode = Prefs.ErrorCode.ERR_NOT_EMPTY;
            Messages.displayError(Prefs.errMessage[errCode.ordinal()], Prefs.ERR_CANNOT_REMOVE);
        }
    }

    @FXML void keyboardHandler(KeyEvent ev) {
        if (filesTable.getSelectionModel().getSelectedIndex() >= 0) {
            switch (ev.getCode()) {
                case DELETE: parentController.tryRemove(); break;
                case ENTER: processItem();
            }
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
        fileSizeColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getSize()));
        fileSizeColumn.setCellFactory(c -> new TableCell<FileInfo, Long>() {
            @Override protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null || empty
                        ? ""
                        : item < 0 ? "[folder]" : String.format("%,d", item));
            }
        });
        fileDateColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue()
                .getModified().format(Prefs.dtFmt)));

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