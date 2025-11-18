/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openrao.data.crac.io.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.opentelemetry.OpenTelemetryReporter;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.data.crac.api.commons.TmpFile;
import com.powsybl.openrao.data.crac.api.io.Importer;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.io.json.deserializers.CracDeserializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.powsybl.commons.json.JsonUtil.createObjectMapper;
import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.TECHNICAL_LOGS;

/**
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
@AutoService(Importer.class)
public class JsonImport implements Importer {
    @Override
    public String getFormat() {
        return "JSON";
    }

    @Override
    public boolean exists(String filename, InputStream inputStream) {
        if (!filename.endsWith(".json")) {
            return false;
        }
        try (var inputData = new TmpFile("crac-import", inputStream)) {
            if (JsonSchemaProvider.isCracFile(inputData.getFileInputStream())) {
                Version cracVersion = readVersion(inputData.getFileInputStream());
                var jsonSchema = JsonSchemaProvider.getSchema(cracVersion);
                //TODO RTE: do we really need to verify here?
                // List<String> validationError = JsonSchemaProvider.getValidationErrors(jsonSchema, inputData.getFileInputStream());
                //TODO
                // if (validationError.isEmpty()) {
                return true;
                // }
                // throw new OpenRaoException("JSON file is not a valid CRAC v%s.%s. Reasons: %s".formatted(cracVersion.majorVersion(), cracVersion.minorVersion(), String.join("; ", validationError)));
            }
            return false;
        } catch (IOException e) {
            TECHNICAL_LOGS.debug("JSON file could not be processed as CRAC. Reason: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public CracCreationContext importData(InputStream inputStream, CracCreationParameters cracCreationParameters, Network network) {
        return OpenTelemetryReporter.withSpan("rao.importJsonCrac", () -> {
            if (network == null) {
                throw new OpenRaoException(
                    "Network object is null but it is needed to map contingency's elements");
            }
            try {
                //TODO
                ObjectMapper objectMapper = createObjectMapper();
                SimpleModule module = new SimpleModule();
                module.addDeserializer(Crac.class,
                    new CracDeserializer(cracCreationParameters.getCracFactory(), network));
                objectMapper.registerModule(module);

                //TODO
                Crac crac = objectMapper.readValue(inputStream, Crac.class);

                CracCreationContext cracCreationContext = new JsonCracCreationContext(true, crac,
                    network.getNameOrId());
                return cracCreationContext;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (OpenRaoException e) {
                CracCreationContext cracCreationContext = new JsonCracCreationContext(false, null,
                    network.getNameOrId());
                cracCreationContext.getCreationReport().error(e.getMessage());
                return cracCreationContext;
            }
        });
    }

    protected Version readVersion(InputStream cracInputStream) {
        byte[] buffer = new byte[4096];
        int bytesRead = 0;
        try {
            bytesRead = cracInputStream.read(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Error reading version", e);
        }
        String cracPrefix = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        return getVersion(cracPrefix);
    }

    protected Version getVersion(String crac) {
        Pattern versionPattern = Pattern.compile("\"version\"\\s?:\\s?\"([1-9]\\d*)\\.(\\d+)\"");
        Matcher versionMatcher = versionPattern.matcher(crac);
        if (!versionMatcher.find()) {
            throw new OpenRaoException("Error parsing version: " + crac);
        }
        return new Version(Integer.parseInt(versionMatcher.group(1)), Integer.parseInt(versionMatcher.group(2)));
    }
}
