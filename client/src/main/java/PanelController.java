import prefs.Prefs;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.control.*;

import java.io.*;

import java.net.URL;

import java.nio.file.*;

import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.*;

public class PanelController implements Initializable {
    @FXML Label nameLabel;
    @FXML ComboBox<String> disks;
    @FXML TextField curPath;
    @FXML TableView<FileInfo> filesTable;
    @FXML Button btnLevelUp;
    @FXML Button btnGoBack;

    private Stack prevPath;
    private int curDiskIdx;
    private boolean serverMode = false;
    private NeStController parentController;
    private List<FileInfo> list;

    //private String uploading, downloading;
    /*public String getUploading() { return uploading; }
    public void setUploading(String uploading) { this.uploading = uploading; }
    public String getDownloading() { return downloading; }
    public void setDownloading(String downloading) { this.downloading = downloading; )

    // источник и приемник (0=клиент/1=сервер) перетаскиваемого (копируемого) файла
    // определяются по видимости списка выбора дисков (невидим у сервера)
    private Byte dragSrc, dragDst;*/

    public void setController (NeStController controller) {
        parentController = controller;
    }

    public List<FileInfo> getList() { return list; }
    public void setList(List<FileInfo> list) { this.list = list; }

    void updateFreeSpace(long freeSpace) {
        nameLabel.setText("Server files ("+(freeSpace/1000/1000)+"M free)");
    }

    void setServerMode() {
        serverMode = true;
        disks.setVisible(false);
        btnLevelUp.setDisable(true);
        btnGoBack.setDisable(true);
        updateFreeSpace(Prefs.MAXSIZE);
        setCurPath(Prefs.serverURL);
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
            filesTable.getItems().clear();
            try (Stream<Path> ps = Files.list(path)) {
                filesTable.getItems().addAll(ps.map(FileInfo::new).collect(Collectors.toList()));
            }
            filesTable.sort();
        }
        catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to update list of files.");
            alert.showAndWait();
        }
    }

    void updateFilesList(String path) {
        Platform.runLater(() -> {
            // если выполнять это не в потоке JavaFX,
            // список на серверной панели будет отображен с дубликатами
            refreshCurPath(path);
            filesTable.getItems().clear();
            if (list != null) filesTable.getItems().addAll(list);
        });
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
        if (prevPath == null) prevPath = new Stack(50);
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

    String getSelectedFilename() {
        return filesTable.isFocused()
                ? filesTable.getSelectionModel().getSelectedItem().getFilename()
                : "";
    }

    String getCurPath() { return curPath.getText(); }
    void setCurPath(String path) {
        refreshCurPath(path);
        updateFilesList(Paths.get(path));
    }

    void refreshCurPath(String path) {
        curPath.setText(path);
        if (curPath.getTooltip() == null) {
            if (!(serverMode && path.equals(Prefs.serverURL)) || path.length() > 0)
                curPath.setTooltip(new Tooltip(path));
        } else {
            if ((serverMode && path.equals(Prefs.serverURL)) || path.length() == 0)
                curPath.setTooltip(null);
            else
                curPath.getTooltip().setText(path);
        }
    }

    /*byte getOwnerPanel() { return (byte)(disks.isVisible() ? 0 : 1); }
    public void dragStarted(MouseEvent mouseEvent) {
        Dragboard db = filesTable.startDragAndDrop(TransferMode.ANY);
        ClipboardContent content = new ClipboardContent();
        content.putFiles(Arrays.asList(new File(getSelectedFilename())));
        if (dragSrc == null) dragSrc = getOwnerPanel();
        System.out.println("source="+dragSrc+" sel="+getSelectedFilename());
        db.setContent(content);
        //mouseEvent.consume();
    }

    public void dragDropped(DragEvent dragEvent) {
        Dragboard db = dragEvent.getDragboard();
        System.out.println("drop: "+db.hasFiles());
        // dragDst==1: копирование с клиента на сервер
        if (db.hasFiles()) {
            dragDst = getOwnerPanel();
            if (dragDst == 0)
                setDownloading(db.getFiles().get(0).toString());
            else
                setUploading(db.getFiles().get(0).toString());
            System.out.println("source="+dragSrc+" target=" + dragDst);
            System.out.println("Dropped: " + db.getFiles().get(0));
            dragSrc = null;
        }
        dragEvent.setDropCompleted(db.hasFiles());
        dragEvent.consume();
    }

    public void dragOver(DragEvent dragEvent) {
        byte dragOver = getOwnerPanel();
        boolean hasFiles = dragEvent.getDragboard().hasFiles();
        System.out.println("over: src="+dragSrc+" dst="+dragOver+" sel="+getSelectedFilename()+
                " files="+hasFiles+"->"+(hasFiles ? dragEvent.getDragboard().getFiles().get(0).toString() : ""));
        // когда перемещение происходит над приемником dragSrc=null (поскольку ранее активировался его dragStarted(),
        // имя выбранного файла пусто
        if (dragEvent.getDragboard().hasFiles() && dragSrc == null) {
            //System.out.println(dragEvent.getGestureSource().toString());
            dragEvent.acceptTransferModes(TransferMode.ANY);
        }
        dragEvent.consume();
    }*/

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
        btnLevelUp.setTooltip(new Tooltip("На уровень вверх"));
        btnGoBack.setTooltip(new Tooltip("Вернуться назад"));

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
        filesTable.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                Path p;
                boolean isFolder;
                if (serverMode) {
                    int i = filesTable.getSelectionModel().getSelectedIndex();
                    p = Paths.get(getCurPath()).resolve(fileNameColumn.getCellData(i));
                    isFolder = fileSizeColumn.getCellData(i).intValue() < 0;
                    if (isFolder)
                        parentController.requestFiles(p.toString());
                } else {
                    p = Paths.get(getCurPath())
                            .resolve(filesTable.getSelectionModel().getSelectedItem().getFilename());
                    isFolder = Files.isDirectory(p);
                    if (isFolder) updateFilesList(p);
                }
                if (isFolder) {
                    pushCurrentPath();
                    btnLevelUp.setDisable(false);
                }
            }
        });

        updateFilesList(startPath);
    }
}