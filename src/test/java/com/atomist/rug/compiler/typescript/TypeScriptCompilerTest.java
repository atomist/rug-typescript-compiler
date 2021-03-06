package com.atomist.rug.compiler.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import javax.script.ScriptException;

import org.junit.Test;

import com.atomist.rug.compiler.CompilerRegistry;
import com.atomist.rug.compiler.ServiceLoaderCompilerRegistry$;
import com.atomist.rug.compiler.typescript.compilation.CompilerFactory;
import com.atomist.source.ArtifactSource;
import com.atomist.source.EmptyArtifactSource;
import com.atomist.source.FileArtifact;
import com.atomist.source.StringFileArtifact;
import com.atomist.source.file.FileSystemArtifactSource;
import com.atomist.source.file.SimpleFileSystemArtifactSourceIdentifier;

import scala.collection.JavaConversions;

public class TypeScriptCompilerTest {

    private String editorTS = "class SimpleEditor  {\n" + "\n" + "    edit() {\n"
            + "        return \"yeah\"\n" + "    }\n" + "}\n" + "";

    private String brokenEditorTS = "class SimpleEditor  {\n" + "\n" + "    edit() {\n"
            + "        let bla = new Test();\n"
            + "        return \"yeah\"\n" + "    }\n" + "}\n" + "";

    @Test
    public void testBrokenCompile() {
        ArtifactSource source = new EmptyArtifactSource("test");
        FileArtifact file = new StringFileArtifact("MyEditor1.ts", JavaConversions.asScalaBuffer(
                Arrays.asList(new String[] { ".atomist", "editors" })), brokenEditorTS);
        source = source.plus(file);
        file = new StringFileArtifact("MyEditor2.ts", JavaConversions.asScalaBuffer(
                Arrays.asList(new String[] { ".atomist", "editors" })), brokenEditorTS);
        source = source.plus(file);

        TypeScriptCompiler compiler = new TypeScriptCompiler(CompilerFactory.create());
        assertTrue(compiler.supports(source));
        try {
            compiler.compile(source);
            fail();
        }
        catch (TypeScriptCompilationException e) {
            System.err.println(e.getMessage());
            assertEquals(".atomist/editors/MyEditor1.ts(4,23): error TS2304: Cannot find name 'Test'.\n" + 
                    "        let bla = new Test();\n" + 
                    "                      ^\n" + 
                    "\n" + 
                    ".atomist/editors/MyEditor2.ts(4,23): error TS2304: Cannot find name 'Test'.\n" + 
                    "        let bla = new Test();\n" + 
                    "                      ^\n" + 
                    "",
                    e.getMessage());
        }
    }

    @Test
    public void testCompile() {
        ArtifactSource source = new EmptyArtifactSource("test");
        FileArtifact file = new StringFileArtifact("MyEditor.ts", JavaConversions
                .asScalaBuffer(Arrays.asList(new String[] { ".atomist", "editors" })), editorTS);
        source = source.plus(file);

        TypeScriptCompiler compiler = new TypeScriptCompiler(CompilerFactory.create(true));
        assertTrue(compiler.supports(source));
        ArtifactSource result = compiler.compile(source);
        assertTrue(result.findFile(".atomist/editors/MyEditor.js").isDefined());
        assertTrue(result.findFile(".atomist/editors/MyEditor.js").get().content()
                .contains("var SimpleEditor = (function () {"));
    }

    @Test
    public void testCompileUserModel() throws ScriptException {
        ArtifactSource source = new FileSystemArtifactSource(
                new SimpleFileSystemArtifactSourceIdentifier(
                        new File("./src/test/resources/my-editor")));

        TypeScriptCompiler compiler = new TypeScriptCompiler(CompilerFactory.create(true));
        assertTrue(compiler.supports(source));
        ArtifactSource result = compiler.compile(source);

        String jsContents = result.findFile(".atomist/editors/SimpleEditor.js").get().content();
        assertTrue(jsContents.contains("var myeditor = new SimpleEditor();"));
    }

    @Test
    public void testMain() throws Exception {
        Path dir = Files.createTempDirectory("compiler-test");
        TypeScriptCompiler.main(new String[] {"src/test/resources/my-editor/.atomist", dir.toString()});
        assertTrue(new File(dir.toFile(), "editors/MyEditor.ts").exists());
    }
    @Test
    public void testCompileAndRunWithModules() throws Exception {
        ArtifactSource source = new FileSystemArtifactSource(
                new SimpleFileSystemArtifactSourceIdentifier(
                        new File("./src/test/resources/licensing-editors")));

        TypeScriptCompiler compiler = new TypeScriptCompiler(CompilerFactory.create(true));
        assertTrue(compiler.supports(source));
        ArtifactSource result = compiler.compile(source);
        String complexJsContents = result.findFile(".atomist/editors/AddLicenseFile.js").get()
                .content();
        assertTrue(complexJsContents.contains("var editor = {"));
    }

    @Test
    public void testCompileThroughCompilerFactory() {
        ArtifactSource source = new EmptyArtifactSource("test");
        FileArtifact file = new StringFileArtifact("MyEditor.ts", JavaConversions
                .asScalaBuffer(Arrays.asList(new String[] { ".atomist", "editors" })), editorTS);
        source = source.plus(file);

        CompilerRegistry registry = ServiceLoaderCompilerRegistry$.MODULE$;
        Collection<com.atomist.rug.compiler.Compiler> compilers = JavaConversions
                .asJavaCollection(registry.findAll(source));
        assertEquals(1, compilers.size());
    }

}
