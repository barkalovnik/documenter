package org.maverick;

import org.maverick.factories.AbstractHTMLFactory;
import org.maverick.factories.IDocumentFactory;
import org.maverick.factories.StandardHTMLFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Универсальный сервис-документатор.
 *
 * <p>На вход подаётся имя класса, его описатель {@link Class} или произвольный объект.
 * Средствами рефлексии строится HTML-документ, описывающий структуру класса
 * (поля, конструкторы, методы, вложенные типы, аннотации), а также структуру всех
 * классов, на которые в объекте есть ссылки или массивы ссылок.</p>
 *
 * <p>Обход рекурсивный; множество {@code documented} хранит уже просмотренные классы,
 * поэтому повторная обработка исключена (в том числе при циклических ссылках
 * вида A -&gt; B -&gt; A).</p>
 *
 * <p>Сторонние библиотеки не используются.</p>
 */
public class ClassDocumenter {

    /** Уже просмотренные (и документируемые) классы — защита от повторной обработки. */
    private final LinkedHashSet<Class<?>> documented = new LinkedHashSet<Class<?>>();

    /** Классы, которые встретились как ссылки, но не документируются (JDK и т.п.). */
    private final Set<Class<?>> external = new LinkedHashSet<Class<?>>();

    private IDocumentFactory factory = new StandardHTMLFactory();

    private boolean includeJdkClasses = false;
    private boolean followMethodSignatures = true;
    private boolean showSyntheticMembers = false;
    private int maxClasses = 500;

    // ------------------------------------------------------------------ настройки

    /** Документировать ли классы стандартной библиотеки (по умолчанию нет). */
    public ClassDocumenter setIncludeJdkClasses(boolean value) {
        this.includeJdkClasses = value;
        return this;
    }

    /** Учитывать ли при обходе типы параметров и возвращаемых значений методов. */
    public ClassDocumenter setFollowMethodSignatures(boolean value) {
        this.followMethodSignatures = value;
        return this;
    }

    /** Показывать ли синтетические элементы, созданные компилятором. */
    public ClassDocumenter setShowSyntheticMembers(boolean value) {
        this.showSyntheticMembers = value;
        return this;
    }

    /** Ограничение на количество документируемых классов (защита от «взрыва» графа). */
    public ClassDocumenter setMaxClasses(int value) {
        this.maxClasses = value;
        return this;
    }

    // ------------------------------------------------------------------ точки входа

    /** Документирование по имени класса. */
    public String document(String className) throws ClassNotFoundException {
        return document(Class.forName(className));
    }

    /** Документирование по произвольному объекту. */
    public String documentObject(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Объект не задан");
        }
        return document(object.getClass());
    }

    /** Документирование по описателю класса. */
    public String document(Class<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("Класс не задан");
        }
        documented.clear();
        external.clear();
        collect(root, true);      // 1-й проход: рекурсивный сбор классов
        return render(root);            // 2-й проход: генерация HTML
    }

    /** Документирование с записью результата в файл. */
    public void documentToFile(Class<?> root, String fileName) throws IOException {
        writeFile(fileName, document(root));
    }

    /** Документирование по имени класса с записью результата в файл. */
    public void documentToFile(String className, String fileName)
            throws ClassNotFoundException, IOException {
        writeFile(fileName, document(className));
    }

    private void writeFile(String fileName, String html) throws IOException {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
            w.write(html);
        }
    }

    // ------------------------------------------------------------------ 1-й проход: обход графа

    /**
     * Рекурсивный сбор классов. Повторно уже просмотренные классы не обрабатываются.
     *
     * @param deep разбирать ли ссылки данного класса дальше
     */
    private void collect(Class<?> clazz, boolean deep) {
        Class<?> c = unwrapArray(clazz);
        if (c == null || c.isPrimitive() || c == void.class) {
            return;
        }
        if (documented.contains(c)) {   // <-- исключение повторной обработки
            return;
        }
        if (!isDocumentable(c)) {
            external.add(c);
            return;
        }
        if (documented.size() >= maxClasses) {
            return;
        }
        documented.add(c);

        DocInfo info = c.getAnnotation(DocInfo.class);
        if (info != null && !info.deep()) {
            deep = false;
        }
        if (!deep) {
            return;
        }

        for (Type t : referencedTypes(c)) {
            for (Class<?> ref : classesOf(t)) {
                collect(ref, true);
            }
        }
    }

    /** Все типы, на которые ссылается класс: предок, интерфейсы, поля, сигнатуры, вложенные типы. */
    private List<Type> referencedTypes(Class<?> c) {
        List<Type> result = new ArrayList<Type>();

        if (c.getGenericSuperclass() != null) {
            result.add(c.getGenericSuperclass());
        }
        Collections.addAll(result, c.getGenericInterfaces());

        for (Field f : c.getDeclaredFields()) {
            if (skipMember(f.getModifiers(), f.isSynthetic(), f.getAnnotations())) {
                continue;
            }
            DocInfo fi = f.getAnnotation(DocInfo.class);
            if (fi != null && !fi.deep()) {
                continue;               // поле показываем, но вглубь не идём
            }
            result.add(f.getGenericType());
        }

        if (followMethodSignatures) {
            for (Constructor<?> ct : c.getDeclaredConstructors()) {
                if (skipMember(ct.getModifiers(), ct.isSynthetic(), ct.getAnnotations())) {
                    continue;
                }
                Collections.addAll(result, ct.getGenericParameterTypes());
            }
            for (Method m : c.getDeclaredMethods()) {
                if (skipMember(m.getModifiers(), m.isSynthetic(), m.getAnnotations())) {
                    continue;
                }
                result.add(m.getGenericReturnType());
                Collections.addAll(result, m.getGenericParameterTypes());
            }
        }

        Collections.addAll(result, c.getDeclaredClasses());
        return result;
    }

    /** Разворачивание сложного типа (дженерики, массивы, wildcard) в набор классов. */
    private Set<Class<?>> classesOf(Type t) {
        Set<Class<?>> out = new LinkedHashSet<Class<?>>();
        classesOf(t, out);
        return out;
    }

    private void classesOf(Type t, Set<Class<?>> out) {
        if (t == null) {
            return;
        }
        if (t instanceof Class) {
            Class<?> c = unwrapArray((Class<?>) t);
            if (c != null && !c.isPrimitive() && c != void.class) {
                out.add(c);
            }
        } else if (t instanceof ParameterizedType p) {
            classesOf(p.getRawType(), out);
            for (Type a : p.getActualTypeArguments()) {
                classesOf(a, out);
            }
        } else if (t instanceof GenericArrayType) {
            classesOf(((GenericArrayType) t).getGenericComponentType(), out);
        } else if (t instanceof WildcardType w) {
            for (Type b : w.getUpperBounds()) {
                classesOf(b, out);
            }
            for (Type b : w.getLowerBounds()) {
                classesOf(b, out);
            }
        } else if (t instanceof TypeVariable) {
            for (Type b : ((TypeVariable<?>) t).getBounds()) {
                classesOf(b, out);
            }
        }
    }

    /** Снятие «массивности»: Foo[][] -> Foo. */
    private Class<?> unwrapArray(Class<?> c) {
        while (c != null && c.isArray()) {
            c = c.getComponentType();
        }
        return c;
    }

    private boolean isDocumentable(Class<?> c) {
        if (c.isPrimitive() || c == void.class) {
            return false;
        }
        if (c.isAnonymousClass() || c.isSynthetic()) {
            return false;
        }
        if (c.isAnnotationPresent(DocIgnore.class)) {
            return false;
        }
        return includeJdkClasses || !isJdkClass(c);
    }

    private boolean isJdkClass(Class<?> c) {
        if (c.getClassLoader() == null) {
            return true;                // загружен bootstrap-загрузчиком
        }
        Package p = c.getPackage();
        String name = (p == null) ? c.getName() : p.getName();
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jakarta.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("com.sun.");
    }

    private boolean skipMember(int modifiers, boolean synthetic, Annotation[] annotations) {
        if (synthetic && !showSyntheticMembers) {
            return true;
        }
        for (Annotation a : annotations) {
            if (a.annotationType() == DocIgnore.class) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 2-й проход: генерация HTML

    private String render(Class<?> root) {
        return factory.render(root, documented, external);
    }

    // ------------------------------------------------------------------ вспомогательные методы

    /** Вторая css-стратегия - пошлые пастельные цвета: оранжевый, зеленый, розовый */
    private String css2() {
        return """
                body{font-family:Segoe UI,Arial,sans-serif;margin:24px;background:#FFCB73;color:#7D0057;}
                h1{font-size:22px;} h2{font-size:18px;margin:0 0 10px;} h3{font-size:14px;margin:16px 0 6px;color:#374151;}
                .card{background:#FFB840;border:1px solid #BF8A30;border-radius:0px;padding:16px 18px;margin:14px 0;}
                table{border-collapse:collapse;width:100%;font-size:13px;margin-bottom:6px;}
                td,th{border:1px solid #BF8A30;padding:5px 8px;text-align:left;vertical-align:top;}
                table.grid th{background:#E065BB;}
                .name{font-family:Consolas,monospace;}
                .meta{color:#912470;font-size:12px;}
                .descr{margin:0 0 10px;font-style:italic;color:#374151;}
                .kind{color:#912470;font-size:12px;}
                .badge{background:#91B52D;color:#fff;font-size:11px;border-radius:0px;padding:2px 6px;}
                .toc{columns:2;font-size:13px;} a{color:#739D00;text-decoration:none;} a:hover{text-decoration:underline;}
                code{background:#eef1f5;border-radius:3px;padding:1px 4px;font-size:12px;}
                """;
    }

    // ------------------------------------------------------------------ запуск из командной строки

    /**
     * Использование: java ClassDocumenter &lt;имя.класса&gt; [выходной.html] [--jdk]
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Использование: java ClassDocumenter <имя.класса> [файл.html] [--jdk]");
            return;
        }
        String className = args[0];
        String out = (args.length > 1 && !args[1].startsWith("--"))
                ? args[1] : simpleName(className) + ".html";

        ClassDocumenter doc = new ClassDocumenter();
        for (String a : args) {
            if ("--jdk".equals(a)) {
                doc.setIncludeJdkClasses(true);
            }
        }
        doc.documentToFile(className, out);
        System.out.println("Документ создан: " + out);
    }

    private static String simpleName(String className) {
        int i = className.lastIndexOf('.');
        return i < 0 ? className : className.substring(i + 1);
    }
}
