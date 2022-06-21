/*
  Урок 2. Java NIO
  1. Реализовать сервер на NIO, поддерживающие команды ls, cat, cd, способный работать через telnet.
  Проще говоря, реализовать простую версию линуксового терминала.
  ls - список файлов в текущей директории на сервере
  cat file - вывести на экран содержание файла с именем в текущей директории
  cd path - перейти в папку с именем
  Внимательно с исключениями, клиент должен понимать что не так если произошла ошибка
*/
import prefs.Prefs;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.util.Iterator;
import java.util.stream.*;

public class NioServer {
    private final ServerSocketChannel server; // канал серверного сокета
    private final Selector selector; // селектор канала

    private Path curPath;

    public NioServer() throws IOException {
        // открытие канала сокета
        server = ServerSocketChannel.open();
        // открытие селектора канала
        selector = Selector.open();
        // связывание канала сокета с адресом сокета
        server.bind(new InetSocketAddress(Prefs.PORT));
        // настроить режим блокировки канала ?
        server.configureBlocking(false);
        // регистрация канала с выбранным селектором на принятие соединений
        server.register(selector, SelectionKey.OP_ACCEPT);
        curPath = Paths.get(Prefs.serverURL);
    }

    public void start() {
        while (server.isOpen()) {
            // выбрать множество ключей для соответствующего канала, готового к операциям ввода/вывода
            try { selector.select(); }
            catch (IOException ex) { System.err.println(ex.getMessage()); }
            // итератор по полученному множеству ключей выбора селектора (может быть пустое)
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            // пока есть ключи выбора
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                // обработать ключ на принятие входящего соединения
                if (key.isAcceptable()) handleAccept();
                // обработать ключ на чтение из входящего соединения
                if (key.isReadable()) handleRead(key);
                // удалить текущий ключ вне зависимости от того, был он обработан или нет;
                // если ключ не удалить (использовать цикл по ключам без итератора),
                // в обработчике принятых входящих соединений будет поднято NPE
                iterator.remove();
            }
        }
    }

    // обработчик чтения из входящего соединения
    private void handleRead(SelectionKey key) {
        // буфер удаляется GC автоматически - нет необходимости
        // (как и возможности) его ручного освобождения
        ByteBuffer buf = ByteBuffer.allocate(Prefs.BUF_SIZE);
        // ссылка на канал (серверного) сокета
        SocketChannel channel = (SocketChannel)key.channel();

        boolean hasData = true;
        StringBuilder s = new StringBuilder();

        // пока канал открыт и во входном потоке есть данные
        while (channel.isOpen() && hasData) {
            try {
                int read = channel.read(buf);
                hasData = read > 0;
                // достигнут конец потока, закрыть канал
                if (read < 0) {
                    channel.close();
                    return;
                }
            } catch (IOException ex) {
                System.err.println(ex.getMessage());
            }
            if (hasData) {
                // установить предельный размер равным текущей позиции, а ее переместить к началу буфера
                buf.flip();
                // побайтно получить все, что было прочитано
                while (buf.hasRemaining()) s.append((char)buf.get());
                buf.clear();
            }
        }

        String[] cmd = s.toString().trim().toLowerCase().split(" +");
        final String changedTo = "current folder has been changed to ";
        String response = "";
        switch (cmd[0]) {
            case Prefs.COM_TERM_CD:
                if (cmd.length == 2)
                    // переход в корневую папку пользователя обработать отдельно
                    // альтернативы: File.separator и FileSystems.getDefault().getSeparator()
                    if (cmd[1].equals(System.getProperty("file.separator"))) {
                        if (Prefs.isRootPath(curPath))
                            response = "already at root folder";
                        else {
                            curPath = Prefs.getRootPath();
                            response = changedTo + "root";
                        }
                    } else {
                        Path dst = curPath.resolve(cmd[1]).normalize();
                        if (Prefs.isValidPath(dst)) {
                            if (Files.exists(dst))
                                if (Files.isDirectory(dst)) {
                                    curPath = dst.normalize();
                                    response = changedTo + cmd[1];
                                } else
                                    response = cmd[1] + " is not a folder";
                            else
                                response = "folder " + cmd[1] + " does not exist";
                        } else
                            response = "wrong path: " + cmd[1];
                    }
                else
                    response = Prefs.getCmdHelp(1);
                break;
            case Prefs.COM_TERM_LIST:
                // получить поток элементов в текущей (вообще - в указанной) папке
                try (Stream<Path> pathStream = Files.list(curPath)) {
                    response = pathStream.map(p -> {
                                String pm = p.getFileName().toString();
                                if (p.toFile().isDirectory()) pm += " <folder>";
                                return pm;
                            }).collect(Collectors.joining("\n\r"));
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
                break;
            case Prefs.COM_TERM_CAT:
                if (cmd.length == 2) {
                    Path p = curPath.resolve(cmd[1]);
                    if (p.toFile().isFile() && Files.isReadable(p)) {
                        try (Stream<String> stream = Files.lines(p)) {
                            stream.forEach(l -> echo(channel, l));
                        } catch (IOException ex) {
                            System.err.println(ex.getMessage());
                        }
                    } else
                        response = "unable to display "+cmd[1];
                } else
                    response = Prefs.getCmdHelp(0);
                break;
            case Prefs.COM_TERM_HELP:
                response = Prefs.getHelp();
                break;
            case Prefs.COM_QUIT:
            case Prefs.COM_EXIT:
                try { channel.close(); }
                catch (IOException ex) { System.err.println(ex.getMessage()); }
                return;
            default: response = s.toString().trim();
        }
        if (response.length() > 0) echo(channel, response);
    }

    // обработчик принятого входящего соединения
    private void handleAccept() {
        // принять входящее соединение с каналом серверного сокета
        try {
            SocketChannel channel = server.accept();
            channel.configureBlocking(false);
            // зарегистрировать селектор канала на операции чтения
            channel.register(selector, SelectionKey.OP_READ);
            echo(channel, "Server terminal is ready.\n\r"+Prefs.getHelp());
        } catch (IOException ex) { System.err.println(ex.getMessage()); }
    }

    private void echo(SocketChannel channel, String msg) {
        try {
            channel.write(ByteBuffer.wrap((msg + "\n\r" + Prefs.terminalPrompt)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ex) { System.err.println(ex.getMessage()); }
    }

    public static void main(String[] args) throws IOException {
        NioServer nioServer = new NioServer();
        System.out.println("Server started");
        nioServer.start();
    }
}