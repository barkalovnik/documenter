package org.maverick;

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
        } else if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            classesOf(p.getRawType(), out);
            for (Type a : p.getActualTypeArguments()) {
                classesOf(a, out);
            }
        } else if (t instanceof GenericArrayType) {
            classesOf(((GenericArrayType) t).getGenericComponentType(), out);
        } else if (t instanceof WildcardType) {
            WildcardType w = (WildcardType) t;
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
        StringBuilder sb = new StringBuilder(64 * 1024);
        String title = "Описание структуры класса " + root.getName();

        sb.append("<!DOCTYPE html>\n<html lang=\"ru\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<title>").append(escape(title)).append("</title>\n")
          .append("<style>\n").append(css()).append("</style>\n")
          .append("</head>\n<body>\n");

        sb.append("<h1>").append(escape(title)).append("</h1>\n");
        sb.append("<p class=\"meta\">Сгенерировано: ")
          .append(escape(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date())))
          .append(" &middot; классов в документе: ").append(documented.size())
          .append("</p>\n");

        renderToc(sb);
        for (Class<?> c : documented) {
            renderClass(sb, c, c == root);
        }
        renderExternal(sb);

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private void renderToc(StringBuilder sb) {
        sb.append("<div class=\"card\"><h2>Содержание</h2>\n<ol class=\"toc\">\n");
        for (Class<?> c : documented) {
            sb.append("<li><a href=\"#").append(anchor(c)).append("\">")
              .append(escape(c.getName())).append("</a> <span class=\"kind\">")
              .append(kindOf(c)).append("</span></li>\n");
        }
        sb.append("</ol></div>\n");
    }

    private void renderExternal(StringBuilder sb) {
        if (external.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<String>();
        for (Class<?> c : external) {
            names.add(c.getName());
        }
        Collections.sort(names);
        sb.append("<div class=\"card\"><h2>Внешние типы (не разбирались)</h2>\n<p class=\"meta\">")
          .append(escape(join(names, ", "))).append("</p></div>\n");
    }

    private void renderClass(StringBuilder sb, Class<?> c, boolean isRoot) {
        sb.append("<div class=\"card\" id=\"").append(anchor(c)).append("\">\n");
        sb.append("<h2>").append(kindOf(c)).append(" ").append(escape(c.getName()));
        if (isRoot) {
            sb.append(" <span class=\"badge\">корневой класс</span>");
        }
        sb.append("</h2>\n");

        DocInfo info = c.getAnnotation(DocInfo.class);
        if (info != null && info.value().length() > 0) {
            sb.append("<p class=\"descr\">").append(escape(info.value())).append("</p>\n");
        }

        // --- общие сведения
        sb.append("<table>\n");
        String pkg = (c.getPackage() == null) ? "" : c.getPackage().getName();
        row(sb, "Пакет", escape(pkg.length() == 0 ? "(пакет по умолчанию)" : pkg));
        row(sb, "Модификаторы", escape(modifiers(c.getModifiers())));
        if (c.getGenericSuperclass() != null) {
            row(sb, "Суперкласс", typeHtml(c.getGenericSuperclass()));
        }
        Type[] ifaces = c.getGenericInterfaces();
        if (ifaces.length > 0) {
            row(sb, "Интерфейсы", typeList(ifaces));
        }
        if (c.getTypeParameters().length > 0) {
            List<String> tp = new ArrayList<String>();
            for (TypeVariable<?> v : c.getTypeParameters()) {
                tp.add(escape(v.getName()));
            }
            row(sb, "Параметры типа", join(tp, ", "));
        }
        Annotation[] ann = c.getAnnotations();
        if (ann.length > 0) {
            row(sb, "Аннотации", annotationsHtml(ann));
        }
        if (info != null && info.author().length() > 0) {
            row(sb, "Автор", escape(info.author()));
        }
        if (info != null && info.since().length() > 0) {
            row(sb, "Версия", escape(info.since()));
        }
        sb.append("</table>\n");

        if (c.isEnum()) {
            renderEnumConstants(sb, c);
        }
        renderFields(sb, c);
        renderConstructors(sb, c);
        renderMethods(sb, c);
        renderReferences(sb, c);

        sb.append("</div>\n");
    }

    private void renderEnumConstants(StringBuilder sb, Class<?> c) {
        Object[] constants = c.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return;
        }
        List<String> names = new ArrayList<String>();
        for (Object o : constants) {
            names.add(escape(String.valueOf(o)));
        }
        sb.append("<h3>Константы перечисления</h3>\n<p>")
          .append(join(names, ", ")).append("</p>\n");
    }

    private void renderFields(StringBuilder sb, Class<?> c) {
        Field[] fields = c.getDeclaredFields();
        sb.append("<h3>Поля</h3>\n");
        if (fields.length == 0) {
            sb.append("<p class=\"meta\">нет</p>\n");
            return;
        }
        sb.append("<table class=\"grid\">\n<tr><th>Модификаторы</th><th>Тип</th>"
                + "<th>Имя</th><th>Аннотации</th><th>Описание</th></tr>\n");
        int shown = 0;
        for (Field f : fields) {
            if (skipMember(f.getModifiers(), f.isSynthetic(), f.getAnnotations())) {
                continue;
            }
            shown++;
            DocInfo fi = f.getAnnotation(DocInfo.class);
            sb.append("<tr><td>").append(escape(modifiers(f.getModifiers())))
              .append("</td><td>").append(typeHtml(f.getGenericType()))
              .append("</td><td class=\"name\">").append(escape(f.getName()))
              .append("</td><td>").append(annotationsHtml(f.getAnnotations()))
              .append("</td><td>").append(fi == null ? "" : escape(fi.value()))
              .append("</td></tr>\n");
        }
        sb.append("</table>\n");
        if (shown == 0) {
            sb.append("<p class=\"meta\">все поля скрыты</p>\n");
        }
    }

    private void renderConstructors(StringBuilder sb, Class<?> c) {
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        sb.append("<h3>Конструкторы</h3>\n");
        if (ctors.length == 0) {
            sb.append("<p class=\"meta\">нет</p>\n");
            return;
        }
        sb.append("<table class=\"grid\">\n<tr><th>Модификаторы</th><th>Параметры</th>"
                + "<th>Исключения</th><th>Аннотации</th></tr>\n");
        for (Constructor<?> ct : ctors) {
            if (skipMember(ct.getModifiers(), ct.isSynthetic(), ct.getAnnotations())) {
                continue;
            }
            sb.append("<tr><td>").append(escape(modifiers(ct.getModifiers())))
              .append("</td><td>").append(params(ct.getGenericParameterTypes()))
              .append("</td><td>").append(typeList(ct.getGenericExceptionTypes()))
              .append("</td><td>").append(annotationsHtml(ct.getAnnotations()))
              .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private void renderMethods(StringBuilder sb, Class<?> c) {
        Method[] methods = c.getDeclaredMethods();
        sb.append("<h3>Методы</h3>\n");
        if (methods.length == 0) {
            sb.append("<p class=\"meta\">нет</p>\n");
            return;
        }
        sb.append("<table class=\"grid\">\n<tr><th>Модификаторы</th><th>Тип результата</th>"
                + "<th>Имя</th><th>Параметры</th><th>Исключения</th>"
                + "<th>Аннотации</th><th>Описание</th></tr>\n");
        for (Method m : methods) {
            if (skipMember(m.getModifiers(), m.isSynthetic(), m.getAnnotations())) {
                continue;
            }
            DocInfo mi = m.getAnnotation(DocInfo.class);
            sb.append("<tr><td>").append(escape(modifiers(m.getModifiers())))
              .append("</td><td>").append(typeHtml(m.getGenericReturnType()))
              .append("</td><td class=\"name\">").append(escape(m.getName()))
              .append("</td><td>").append(params(m.getGenericParameterTypes()))
              .append("</td><td>").append(typeList(m.getGenericExceptionTypes()))
              .append("</td><td>").append(annotationsHtml(m.getAnnotations()))
              .append("</td><td>").append(mi == null ? "" : escape(mi.value()))
              .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    /** Список классов документа, на которые ссылается данный класс. */
    private void renderReferences(StringBuilder sb, Class<?> c) {
        Set<Class<?>> refs = new LinkedHashSet<Class<?>>();
        for (Type t : referencedTypes(c)) {
            for (Class<?> r : classesOf(t)) {
                if (r != c && documented.contains(r)) {
                    refs.add(r);
                }
            }
        }
        sb.append("<h3>Ссылки на другие классы документа</h3>\n");
        if (refs.isEmpty()) {
            sb.append("<p class=\"meta\">нет</p>\n");
            return;
        }
        List<String> links = new ArrayList<String>();
        for (Class<?> r : refs) {
            links.add("<a href=\"#" + anchor(r) + "\">" + escape(r.getSimpleName()) + "</a>");
        }
        sb.append("<p>").append(join(links, ", ")).append("</p>\n");
    }

    // ------------------------------------------------------------------ вспомогательные методы

    private void row(StringBuilder sb, String key, String value) {
        sb.append("<tr><th>").append(escape(key)).append("</th><td>")
          .append(value).append("</td></tr>\n");
    }

    private String params(Type[] types) {
        if (types.length == 0) {
            return "<span class=\"meta\">()</span>";
        }
        return typeList(types);
    }

    private String typeList(Type[] types) {
        List<String> parts = new ArrayList<String>();
        for (Type t : types) {
            parts.add(typeHtml(t));
        }
        return join(parts, ", ");
    }

    /** Имя типа с гиперссылками на документированные классы. */
    private String typeHtml(Type t) {
        String text = escape(t.getTypeName());

        List<Class<?>> refs = new ArrayList<Class<?>>();
        for (Class<?> c : classesOf(t)) {
            if (documented.contains(c)) {
                refs.add(c);
            }
        }
        // длинные имена подставляем первыми, чтобы вложенные имена не ломали разметку
        Collections.sort(refs, new Comparator<Class<?>>() {
            public int compare(Class<?> a, Class<?> b) {
                return b.getName().length() - a.getName().length();
            }
        });

        List<String> links = new ArrayList<String>();
        for (Class<?> c : refs) {
            String name = escape(c.getTypeName());
            if (text.indexOf(name) < 0) {
                continue;
            }
            String token = "\u0001" + links.size() + "\u0001";
            links.add("<a href=\"#" + anchor(c) + "\" title=\"" + name + "\">"
                    + escape(c.getSimpleName()) + "</a>");
            text = text.replace(name, token);
        }
        for (int i = 0; i < links.size(); i++) {
            text = text.replace("\u0001" + i + "\u0001", links.get(i));
        }
        return text;
    }

    private String annotationsHtml(Annotation[] annotations) {
        if (annotations.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<String>();
        for (Annotation a : annotations) {
            parts.add("<code>@" + escape(a.annotationType().getSimpleName()) + "</code>");
        }
        return join(parts, " ");
    }

    private String kindOf(Class<?> c) {
        if (c.isAnnotation()) {
            return "аннотация";
        }
        if (c.isInterface()) {
            return "интерфейс";
        }
        if (c.isEnum()) {
            return "перечисление";
        }
        return "класс";
    }

    private String modifiers(int mod) {
        String s = Modifier.toString(mod);
        return s.isEmpty() ? "(package-private)" : s;
    }

    private String anchor(Class<?> c) {
        return c.getName().replace('.', '_').replace('$', '_');
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:  sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String css() {
        return """
                body{font-family:Segoe UI,Arial,sans-serif;margin:24px;background:#f5f6f8;color:#1c1e21;}
                h1{font-size:22px;} h2{font-size:18px;margin:0 0 10px;} h3{font-size:14px;margin:16px 0 6px;color:#374151;}
                .card{background:#fff;border:1px solid #d9dde3;border-radius:8px;padding:16px 18px;margin:14px 0;}
                table{border-collapse:collapse;width:100%;font-size:13px;margin-bottom:6px;}
                td,th{border:1px solid #e2e5ea;padding:5px 8px;text-align:left;vertical-align:top;}
                table.grid th{background:#eef1f5;}
                .name{font-family:Consolas,monospace;}
                .meta{color:#6b7280;font-size:12px;}
                .descr{margin:0 0 10px;font-style:italic;color:#374151;}
                .kind{color:#6b7280;font-size:12px;}
                .badge{background:#2563eb;color:#fff;font-size:11px;border-radius:4px;padding:2px 6px;}
                .toc{columns:2;font-size:13px;} a{color:#1d4ed8;text-decoration:none;} a:hover{text-decoration:underline;}
                code{background:#eef1f5;border-radius:3px;padding:1px 4px;font-size:12px;}
                """;
    }

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
