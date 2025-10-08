package com.powsybl.openrao.data.crac.api.commons;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TmpFile implements AutoCloseable {

    private final File tempFile;

    public TmpFile(String suffix) throws IOException {
        this.tempFile = File.createTempFile("powsybl-openrao", suffix + ".tmp");
        tempFile.deleteOnExit();
    }

    public TmpFile(String suffix, InputStream inputData) throws IOException {
        this(suffix);
        loadInputStream(inputData);
    }

    public TmpFile(String suffix, File inputData) throws IOException {
        this(suffix, new FileInputStream(inputData));
    }

    protected void loadInputStream(InputStream inputStream) throws IOException {
        try (OutputStream out = new FileOutputStream(tempFile)) {
            inputStream.transferTo(out);
        }
    }

    @Override
    public void close() {
        try {
            this.tempFile.delete();
        } catch (Exception e) {
            // ignore
        }
    }

    public InputStream getFileInputStream() {
        try {
            return new FileInputStream(tempFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Temp file not found", e);
        }
    }

    public OutputStream getOutputStream() {
        try {
            return new FileOutputStream(tempFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Temp file not found", e);
        }
    }

}
