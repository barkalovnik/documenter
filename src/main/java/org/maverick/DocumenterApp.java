package org.maverick;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GUI-оболочка для {@link ClassDocumenter} на JavaFX.
 *
 * <p>Позволяет выбрать скомпилированный {@code .class} файл, автоматически
 * определяет по его байткоду полное имя класса и предполагаемый корень
 * classpath, загружает класс через {@link URLClassLoader} и строит HTML-документ,
 * который отображается в {@link WebView} и может быть сохранён на диск.</p>
 */
public class DocumenterApp extends Application {

    private final TextField classpathField = new TextField();
    private final TextField classFileField = new TextField();
    private final TextField classNameField = new TextField();
    private final CheckBox includeJdkBox = new CheckBox("Показывать классы JDK");
    private final Label statusLabel = new Label("Выберите .class файл");
    private final WebView webView = new WebView();
    private final Button saveButton = new Button("Сохранить HTML...");

    private String lastHtml;
    private File lastClassFile;

    @Override
    public void start(Stage stage) {
        classpathField.setPromptText("Корневой каталог classpath (определяется автоматически)");
        classFileField.setEditable(false);
        classFileField.setPromptText("Файл .class не выбран");
        classNameField.setPromptText("Полное имя класса (определяется автоматически из байткода)");

        Button pickClassButton = new Button("Выбрать .class файл...");
        pickClassButton.setOnAction(e -> onPickClassFile(stage));

        Button pickRootButton = new Button("Каталог classpath...");
        pickRootButton.setOnAction(e -> onPickRoot(stage));

        Button documentButton = new Button("Документировать");
        documentButton.setOnAction(e -> onDocument());

        saveButton.setDisable(true);
        saveButton.setOnAction(e -> onSave(stage));

        HBox filePane = new HBox(8, new Label("Файл:"), classFileField, pickClassButton);
        HBox.setHgrow(classFileField, Priority.ALWAYS);

        HBox rootPane = new HBox(8, new Label("Classpath:"), classpathField, pickRootButton);
        HBox.setHgrow(classpathField, Priority.ALWAYS);

        HBox namePane = new HBox(8, new Label("Класс:"), classNameField, includeJdkBox);
        HBox.setHgrow(classNameField, Priority.ALWAYS);

        HBox actionPane = new HBox(8, documentButton, saveButton);

        VBox top = new VBox(6, filePane, rootPane, namePane, actionPane, statusLabel);
        top.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(webView);

        stage.setTitle("Документатор классов (рефлексия + аннотации)");
        stage.setScene(new Scene(root, 1024, 720));
        stage.show();
    }

    // ------------------------------------------------------------------ обработчики

    private void onPickClassFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите скомпилированный .class файл");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Class files (*.class)", "*.class"));
        if (lastClassFile != null && lastClassFile.getParentFile() != null
                && lastClassFile.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(lastClassFile.getParentFile());
        }

        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        lastClassFile = file;
        classFileField.setText(file.getAbsolutePath());

        try {
            String fqName = readClassNameFromBytecode(file.toPath());
            classNameField.setText(fqName);

            Path guessedRoot = guessClasspathRoot(file.toPath(), fqName);
            if (guessedRoot != null) {
                classpathField.setText(guessedRoot.toString());
            }
            statusLabel.setText("Класс определён: " + fqName + ". Проверьте classpath и нажмите «Документировать».");
        } catch (IOException ex) {
            statusLabel.setText("Не удалось прочитать имя класса из байткода: " + ex.getMessage());
        }
    }

    private void onPickRoot(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите корневой каталог classpath");
        if (lastClassFile != null && lastClassFile.getParentFile() != null) {
            chooser.setInitialDirectory(lastClassFile.getParentFile());
        }
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            classpathField.setText(dir.getAbsolutePath());
        }
    }

    private void onDocument() {
        String className = safeTrim(classNameField.getText());
        String rootText = safeTrim(classpathField.getText());

        if (className.isEmpty()) {
            statusLabel.setText("Не задано имя класса.");
            return;
        }

        URLClassLoader created = null;
        try {
            ClassLoader parent = Thread.currentThread().getContextClassLoader();
            ClassLoader loader = parent;
            if (!rootText.isEmpty()) {
                URL rootUrl = Paths.get(rootText).toUri().toURL();
                created = new URLClassLoader(new URL[]{rootUrl}, parent);
                loader = created;
            }

            Class<?> clazz = Class.forName(className, true, loader);

            ClassDocumenter documenter = new ClassDocumenter()
                    .setIncludeJdkClasses(includeJdkBox.isSelected());

            String html = documenter.document(clazz);
            lastHtml = html;
            webView.getEngine().loadContent(html, "text/html");
            saveButton.setDisable(false);
            statusLabel.setText("Документ сформирован для класса " + clazz.getName() + ".");
        } catch (ClassNotFoundException ex) {
            statusLabel.setText("Класс не найден: проверьте имя класса и корневой каталог classpath.");
        } catch (LinkageError ex) {
            statusLabel.setText("Ошибка компоновки (не найден один из зависимых классов): " + ex.getMessage());
        } catch (Exception ex) {
            statusLabel.setText("Ошибка: " + ex);
        } finally {
            if (created != null) {
                try {
                    created.close();
                } catch (IOException ignored) {
                    // classloader уже отработал, закрытие носит вспомогательный характер
                }
            }
        }
    }

    private void onSave(Stage stage) {
        if (lastHtml == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить документ");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("HTML files (*.html)", "*.html"));

        String suggested = classNameField.getText();
        if (suggested != null && !suggested.isEmpty()) {
            int dot = suggested.lastIndexOf('.');
            chooser.setInitialFileName((dot < 0 ? suggested : suggested.substring(dot + 1)) + ".html");
        }

        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            Files.write(file.toPath(), lastHtml.getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("Сохранено: " + file.getAbsolutePath());
        } catch (IOException ex) {
            statusLabel.setText("Не удалось сохранить файл: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ вспомогательная логика

    /**
     * Предполагает корень classpath по расположению {@code .class} файла и известному
     * полному имени класса: поднимается вверх ровно на столько каталогов, сколько
     * сегментов в имени пакета.
     */
    private Path guessClasspathRoot(Path classFile, String fqName) {
        Path dir = classFile.getParent();
        if (dir == null) {
            return null;
        }
        int lastDot = fqName.lastIndexOf('.');
        if (lastDot < 0) {
            return dir; // класс в пакете по умолчанию — файл уже лежит в корне
        }
        String pkg = fqName.substring(0, lastDot);
        int segments = pkg.split("\\.").length;

        Path root = dir;
        for (int i = 0; i < segments && root != null; i++) {
            root = root.getParent();
        }
        return root;
    }

    /**
     * Читает полное (бинарное) имя класса непосредственно из структуры {@code .class}
     * файла — из константного пула, по индексу {@code this_class}. Загрузка самого
     * класса при этом не выполняется.
     */
    private static String readClassNameFromBytecode(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {

            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                throw new IOException("неверная сигнатура файла (не .class)");
            }
            in.readUnsignedShort();                 // minor_version
            in.readUnsignedShort();                 // major_version

            int cpCount = in.readUnsignedShort();
            String[] utf8 = new String[cpCount];
            int[] classNameIndex = new int[cpCount];

            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1:  utf8[i] = in.readUTF(); break;                     // Utf8
                    case 7:  classNameIndex[i] = in.readUnsignedShort(); break; // Class
                    case 8:  in.readUnsignedShort(); break;                    // String
                    case 3:  case 4: in.readInt(); break;                      // Integer, Float
                    case 5:  case 6: in.readLong(); i++; break;                // Long, Double — 2 слота
                    case 9: case 10: case 11: case 12:                         // *ref, NameAndType
                    case 17: case 18:                                          // Dynamic, InvokeDynamic
                        in.readInt(); break;
                    case 15: in.readByte(); in.readUnsignedShort(); break;     // MethodHandle
                    case 16: in.readUnsignedShort(); break;                    // MethodType
                    case 19: case 20: in.readUnsignedShort(); break;           // Module, Package
                    default:
                        throw new IOException("неизвестный тег константного пула: " + tag);
                }
            }

            in.readUnsignedShort();                 // access_flags
            int thisClass = in.readUnsignedShort();
            int nameIndex = classNameIndex[thisClass];
            String internalName = utf8[nameIndex];
            if (internalName == null) {
                throw new IOException("не удалось определить имя класса");
            }
            return internalName.replace('/', '.');
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
