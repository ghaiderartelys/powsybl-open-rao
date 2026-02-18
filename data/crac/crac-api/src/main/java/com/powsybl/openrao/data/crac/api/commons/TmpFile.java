/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openrao.data.crac.api.commons;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Georg haider {@literal <georg.haider at artelys.com>}
 */
public class TmpFile implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TmpFile.class);

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
        try (var is = inputStream; OutputStream out = new FileOutputStream(tempFile)) {
            is.transferTo(out);
        }
        LOGGER.debug("Loaded data. Size={}", tempFile.length());
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
