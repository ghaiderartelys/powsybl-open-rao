package com.powsybl.openrao.commons.opentelemetry;

import com.powsybl.openrao.commons.OpenRaoException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.util.Collection;
import java.util.concurrent.Callable;

public final class OpenTelemetryReporter {

    public static final String OPEN_RAO = "open-rao";

    public static final String VERSION = "1.0.0";
    /**
     * The open telemetry tracer
     */
    private static Tracer TRACER;

    /**
     * Logger for the spans creation
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryReporter.class);

    /**
     * Private constructor
     */
    private OpenTelemetryReporter() {

    }

    /**
     * Initializes the OpenTelemetry SDK with a simple logging exporter.
     * In a production environment, you would use an exporter like OTLP
     * to send data to a backend (e.g., Jaeger, Prometheus, etc.).
     *
     * @param endPoint the Open Telemetry trace collector (ex: '<a href="http://localhost:4317">...</a>')
     * @param service the service name for traces
     * @param version the version of the service for traces
     */
    public static void initOpenTelemetry(String endPoint, String service, String version) {

        OtlpGrpcSpanExporter otlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder().setEndpoint(endPoint).build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(otlpGrpcSpanExporter).build())
                .setResource(Resource.getDefault().merge(
                        Resource.builder()
                                .put("service.name", service != null ? service : OPEN_RAO)
                                .put("service.version", version != null ? version : VERSION).build()
                )).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        TRACER = tracerProvider.get(OPEN_RAO);
    }

    /**
     * Set the open telemetry tracer used to trace open-rao
     *
     * @param tracerProvider
     */
    public static void setOpenTelemetryTracer(SdkTracerProvider tracerProvider) {
        TRACER = tracerProvider.get(OPEN_RAO);
    }

    @SuppressWarnings("unused")
    private static SpanExporter getStdIoSpanExporter() {
        return new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> collection) {
                for (SpanData spanData : collection) {
                    LOGGER.info("[{}] >> {}\t{}\t{}\t{} ms", spanData.getParentSpanId(), spanData.getSpanId(), spanData.getName(), spanData.getStatus(), Math.ceil((double) (spanData.getEndEpochNanos() - spanData.getStartEpochNanos()) / 1000000));
                }
                return null;
            }

            @Override
            public CompletableResultCode flush() {
                return null;
            }

            @Override
            public CompletableResultCode shutdown() {
                return null;
            }
        };
    }

    /**
     * A generic utility method to wrap a method call with a span.
     * This method handles span creation, context management, and error handling.
     *
     * @param spanName The name of the span to create.
     * @param callable The operation to execute, wrapped in a Callable.
     * @param <T>      The return type of the Callable.
     * @return The result of the Callable.
     * @throws Exception if the Callable throws an exception.
     */
    public static <T> T withSpan(String spanName, Callable<T> callable) {
        if (TRACER != null) {
            Span span = TRACER.spanBuilder(spanName).startSpan();
            try (Scope scope = span.makeCurrent()) {
                span.addEvent("Executing operation: " + spanName);
                T result = callable.call();
                span.setStatus(StatusCode.OK);
                return result;
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, "Operation failed: " + e.getMessage());
                span.recordException(e);
                throw new OpenRaoException(e.getMessage());
            } finally {
                span.end();
            }
        } else {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new OpenRaoException(e.getMessage());
            }
        }
    }

    public static void withSpan(String spanName, Runnable runnable) {
        withSpan(spanName, runnable, false);
    }

    /**
     * An overloaded utility method for methods that return void.
     *
     * @param spanName The name of the span to create.
     * @param runnable The operation to execute, wrapped in a Runnable.
     */
    public static void withSpan(String spanName, Runnable runnable, boolean error) {
        if (TRACER != null) {
            Span span = TRACER.spanBuilder(spanName).startSpan();
            if (error) {
                span.setStatus(StatusCode.ERROR);
            }
            try (Scope scope = span.makeCurrent()) {
                span.addEvent("Executing operation: " + spanName);
                runnable.run();
                span.setStatus(StatusCode.OK);
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, "Operation failed: " + e.getMessage());
                span.recordException(e);
                throw new OpenRaoException(e.getMessage());
            } finally {
                span.end();
            }
        } else {
            runnable.run();
        }
    }

    public static void trace(Logger logger, String format, Object... arguments) {
        OpenTelemetryReporter.withSpan(format(format, arguments), () -> logger.trace(format, arguments));
    }

    public static void info(Logger logger, String format, Object... arguments) {
        OpenTelemetryReporter.withSpan(format(format, arguments), () -> logger.info(format, arguments));
    }

    public static void warn(Logger logger, String format, Object... arguments) {
        OpenTelemetryReporter.withSpan(format(format, arguments), () -> logger.warn(format, arguments));
    }

    public static void error(Logger logger, String format, Object... arguments) {
        OpenTelemetryReporter.withSpan(format(format, arguments), () -> logger.error(format, arguments), true);
    }

    public static void debug(Logger logger, String format, Object... arguments) {
        OpenTelemetryReporter.withSpan(format(format, arguments), () -> logger.debug(format, arguments));
    }

    public static String format(String pattern, Object[] args) {
        // Use the SLF4J MessageFormatter's arrayFormat method
        return MessageFormatter.arrayFormat(pattern, args).getMessage();
    }

}
