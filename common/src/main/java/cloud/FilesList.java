package cloud;

import java.io.IOException;

import java.nio.file.*;

import java.util.List;
import java.util.stream.Collectors;

public class FilesList implements CloudMessage {
    private final List<String> files;

    public FilesList(Path path) throws IOException {
        files = Files.list(path)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    public List<String> getFiles() { return files; }
}