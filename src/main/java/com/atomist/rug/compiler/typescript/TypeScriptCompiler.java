package com.atomist.rug.compiler.typescript;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.atomist.project.archive.DefaultAtomistConfig$;
import com.atomist.rug.compiler.Compiler;
import com.atomist.source.ArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.StringFileArtifact;

import de.undercouch.vertx.lang.typescript.TypeScriptClassLoader;
import de.undercouch.vertx.lang.typescript.cache.InMemoryCache;
import de.undercouch.vertx.lang.typescript.compiler.EngineCompiler;
import de.undercouch.vertx.lang.typescript.compiler.NodeCompiler;
import de.undercouch.vertx.lang.typescript.compiler.Source;
import de.undercouch.vertx.lang.typescript.compiler.SourceFactory;
import de.undercouch.vertx.lang.typescript.compiler.V8Compiler;
import scala.collection.JavaConversions;

public class TypeScriptCompiler implements Compiler {

    private de.undercouch.vertx.lang.typescript.compiler.TypeScriptCompiler compiler;

    @Override
    public ArtifactSource compile(ArtifactSource source) {
        if (compiler == null) {
            initCompiler();
        }
        SourceFactory sourceFactory = new ArtifactSourceSourceFactory(source, compiler);

        List<FileArtifact> files = filterSourceFiles(source);
        List<FileArtifact> compiledFiles = files.stream().map(f -> {
            try {
                String compiled = compiler.compile(f.path(), sourceFactory);
                return new StringFileArtifact(f.name().replace(".ts", ".js"), f.pathElements(),
                        compiled);
            }
            catch (IOException e) {
                // handle exception
                e.printStackTrace();
            }
            catch (RuntimeException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());

        ArtifactSource result = source;
        for (FileArtifact compileFile : compiledFiles.stream().filter(c -> c != null)
                .collect(Collectors.toList())) {
            result = result.plus(compileFile);
        }

        return result;
    }

    protected List<FileArtifact> filterSourceFiles(ArtifactSource source) {
        List<FileArtifact> files = JavaConversions.asJavaCollection(source.allFiles()).stream()
                .filter(f -> f.path().startsWith(DefaultAtomistConfig$.MODULE$.atomistRoot())
                        && f.name().endsWith(".ts"))
                .collect(Collectors.toList());
        return files;
    }

    @Override
    public boolean supports(ArtifactSource source) {
        return !filterSourceFiles(source).isEmpty();
    }

    private synchronized void initCompiler() {
        if (V8Compiler.supportsV8()) {
            compiler = new V8Compiler();
        }
        else if (NodeCompiler.supportsNode()) {
            compiler = new NodeCompiler();
        }
        else {
            compiler = new EngineCompiler();
        }
    }

    private static class ArtifactSourceSourceFactory implements SourceFactory {

        private final ArtifactSource source;
        private final SourceFactory parentSourceFactory;

        public ArtifactSourceSourceFactory(ArtifactSource source,
                de.undercouch.vertx.lang.typescript.compiler.TypeScriptCompiler compiler) {
            this.source = source;
            this.parentSourceFactory = new TypeScriptClassLoader(getClass().getClassLoader(),
                    compiler, new InMemoryCache());
        }

        @Override
        public Source getSource(String filename, String baseFilename) throws IOException {
            if (source.findFile(filename).isDefined()) {
                return new Source(URI.create(filename), source.findFile(filename).get().content());
            }
            else {
                // tsc searches for dependencies in node_modules directory.
                // we are remapping this onto the classpath
                if (filename.startsWith("node_modules/")) {
                    filename = filename.replace("node_modules/", "");
                }

                // First try the classpath
                InputStream is = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(filename);
                if (is != null) {
                    return Source.fromStream(is, URI.create(filename), StandardCharsets.UTF_8);
                }
                return parentSourceFactory.getSource(filename, baseFilename);

                // Second iterate up the working directory and search for node_modules
                // try {
                // return parentSourceFactory.getSource(filename, baseFilename);
                // }
                // catch (Exception e) {
                // File root = new File(System.getProperty("user.dir"));
                // while (root.getParent() != null) {
                // File nodeModules = new File(root, "node_modules");
                // if (nodeModules.exists()) {
                // try {
                // return parentSourceFactory.getSource(
                // root.getAbsolutePath() + File.separator + filename,
                // baseFilename);
                // }
                // catch (Exception ex) {}
                // }
                // root = root.getParentFile();
                // }
                // }
            }
        }
    }
}
