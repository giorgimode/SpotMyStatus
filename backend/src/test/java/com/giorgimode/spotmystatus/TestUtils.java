package com.giorgimode.spotmystatus;

import java.io.File;
import java.nio.file.Files;
import org.springframework.util.ResourceUtils;

public class TestUtils {

    public static String getFileContent(String extractionResponseFile) throws java.io.IOException {
        File extractionFile = ResourceUtils.getFile("classpath:" + extractionResponseFile);
        return Files.readString(extractionFile.toPath());
    }
}
