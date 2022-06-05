import prefs.Prefs;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URL;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

import java.util.ResourceBundle;
import java.util.stream.Collectors;

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
    /*private String uploading, downloading;
    private NeStController parentController;

    public String getUploading() { return uploading; }
    public void setUploading(String uploading) { this.uploading = uploading; }
    public String getDownloading() { return downloading; }
    public void setDownloading(String downloading) { this.downloading = downloading; )

    // источник и приемник (0=клиент/1=сервер) перетаскиваемого (копируемого) файла
    // определяются по видимости списка выбора дисков (невидим у сервера)
    private Byte dragSrc, dragDst;*/

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
        pushCurrentPath(true);
    }

    void setLocalMode(String path) {
        serverMode = false;
        nameLabel.setText("Client files");
        disks.setVisible(true);
        btnLevelUp.setDisable(Paths.get(path).getParent() == null);
        btnGoBack.setDisable(true);
        setCurPath(path);
        if (prevPath != null) prevPath.clear();
        pushCurrentPath(true);
    }

    boolean isServerMode() { return serverMode; }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        btnLevelUp.setTooltip(new Tooltip("На уровень вверх"));
        btnGoBack.setTooltip(new Tooltip("Вернуться назад"));

        TableColumn<FileInfo, FileInfo.FileType> fileTypeColumn = new TableColumn<>();
        TableColumn<FileInfo, String> fileNameColumn = new TableColumn<>("Name");
        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Size");
        TableColumn<FileInfo, String> fileDateColumn = new TableColumn<>("Date");

        fileTypeColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getType()));
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
                .getModified().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

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
        filesTable.getSortOrder().add(fileTypeColumn);
        filesTable.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                Path p = Paths.get(getCurPath()).resolve(filesTable.getSelectionModel().getSelectedItem().getFilename());
                if (Files.isDirectory(p)) {
                    pushCurrentPath(false);
                    btnLevelUp.setDisable(false);
                    updateFilesList(p);
                }
            }
        });

        updateFilesList(startPath);
    }

    void updateFilesList(Path path) {
        try {
            curPath.setText(path.normalize().toAbsolutePath().toString());
            filesTable.getItems().clear();
            filesTable.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            filesTable.sort();
        }
        catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to update list of files.");
            alert.showAndWait();
        }
    }

    @FXML void cmbxChangeDisk(/*ActionEvent ev*/) {
        if (disks.getSelectionModel().getSelectedIndex() != curDiskIdx) {
            pushCurrentPath(false);
            updateFilesList(Paths.get(disks.getSelectionModel().getSelectedItem()));
            curDiskIdx = disks.getSelectionModel().getSelectedIndex();
            btnLevelUp.setDisable(true);
            btnGoBack.setDisable(prevPath.isEmpty());
        }
    }

    void pushCurrentPath(boolean canNotGoBack) {
        if (prevPath == null) prevPath = new Stack(50);
        prevPath.push(getCurPath());
        btnGoBack.setDisable(canNotGoBack);
    }

    @FXML void btnLevelUpAction(/*ActionEvent ev*/) {
        pushCurrentPath(false);
        Path parentPath = Paths.get(getCurPath()).getParent();
        if (parentPath != null) {
            updateFilesList(parentPath);
            btnLevelUp.setDisable(serverMode
                    ? getCurPath().equals(Prefs.serverURL) //TODO
                    : Paths.get(parentPath.toString()).getParent() == null);
        }
    }

    public void btnGoBackAction(/*ActionEvent ev*/) {
        Path prev = Paths.get(prevPath.pop());
        updateFilesList(prev);
        btnGoBack.setDisable(prevPath.isEmpty());
        btnLevelUp.setDisable(serverMode ? getCurPath().equals(Prefs.serverURL) : prev.getParent() == null);
    }

    String getSelectedFilename() {
        return filesTable.isFocused()
                ? filesTable.getSelectionModel().getSelectedItem().getFilename()
                : "";
    }

    String getCurPath() { return curPath.getText(); }
    void setCurPath(String path) {
        curPath.setText(path);
        updateFilesList(Paths.get(path));
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
}