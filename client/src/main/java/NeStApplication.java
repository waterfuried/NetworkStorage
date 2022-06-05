/*
  Разработка сетевого хранилища на Java
  Урок 1. Проектирование архитектуры
  1. Написать клиента на javaFx и сервер на java.
     Клиент должен уметь отображать список файлов на клиенте и сервере.
  2. Передать файл с клиента на сервер (Смотри последние 20 минут урока)
*/
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import prefs.Prefs;

public class NeStApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle(Prefs.FULL_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) { launch(); }
}