package com.atomist.rug.compiler.typescript;

import static java.util.stream.Collectors.toList;
import static scala.collection.JavaConversions.asJavaCollection;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atomist.rug.compiler.Compiler;
import com.atomist.rug.compiler.typescript.compilation.NashornCompiler;
import com.atomist.rug.compiler.typescript.compilation.V8Compiler;
import com.atomist.source.Artifact;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.StringFileArtifact;

import scala.collection.JavaConversions;

public class TypeScriptCompiler implements Compiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypeScriptCompiler.class);
    private static final boolean DISABLE_V8 = (System.getenv("RUG_DISABLE_V8") != null);
    private static final boolean DISABLE_NASHORN = (System.getenv("RUG_DISABLE_NASHORN") != null);

    private com.atomist.rug.compiler.typescript.compilation.Compiler compiler;

    @Override
    public ArtifactSource compile(ArtifactSource source) {
        if (compiler == null) {
            initCompiler();
        }
        try {
            DefaultSourceFileLoader defaultLoader = new DefaultSourceFileLoader(compiler);
            SourceFileLoader artifactSourceLoader = new ArtifactSourceSourceFileLoader(source,
                    defaultLoader);

            List<FileArtifact> files = filterSourceFiles(source);
            List<FileArtifact> compiledFiles = files.stream().map(f -> {
                try {
                    String compiled = compiler.compile(f.path(), artifactSourceLoader);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Successfully compiled typescript {} to \n{}", f.path(),
                                compiled);
                    }
                    return new StringFileArtifact(f.name().replace(".ts", ".js"), f.pathElements(),
                            compiled);
                }
                catch (Exception e) {
                    handleException(e);
                }
                return null;
            }).collect(toList());

            List<Artifact> artifacts = compiledFiles.stream().filter(Objects::nonNull)
                    .collect(toList());
            return source.plus(JavaConversions.asScalaBuffer(artifacts));
        }
        finally {
            compiler.shutdown();
            compiler = null;
        }
    }

    @Override
    public boolean supports(ArtifactSource source) {
        return !filterSourceFiles(source).isEmpty();
    }

    protected List<FileArtifact> filterSourceFiles(ArtifactSource source) {
        return asJavaCollection(source.allFiles()).stream()
                .filter(f -> f.path().startsWith(".atomist/") && f.name().endsWith(".ts"))
                .filter(f -> !f.path().startsWith(".atomist/node_modules/")).collect(toList());
    }

    private void handleException(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("<#>")) {
            int start = msg.indexOf("<#>") + 3;
            int end = msg.lastIndexOf("<#>");
            throw new TypeScriptCompilationException(msg.substring(start, end).trim());
        }
        else {
            throw new TypeScriptCompilationException("Compilation failed", e);
        }
    }

    private synchronized void initCompiler() {
        if (V8Compiler.IS_ENABLED && !DISABLE_V8) {
            compiler = new V8Compiler();
        }
        else if (NashornCompiler.IS_ENABLED && !DISABLE_NASHORN) {
            compiler = new NashornCompiler();
        }
        else {
            throw new TypeScriptCompilationException("No suitable compiler available");
        }
        compiler.init();
    }
}
