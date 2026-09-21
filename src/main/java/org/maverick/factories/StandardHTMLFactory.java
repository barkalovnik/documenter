package org.maverick.factories;

import org.maverick.ClassRenderRecord;
import org.maverick.DocInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Создаёт обычную html страничку с описанием всех доступных полей
 */
public class StandardHTMLFactory extends AbstractHTMLFactory {
    /**
     * Создание по классо-описателю строки html
     * @param root описатель класса
     * @return {String} html
     */
    @Override
    public String render(Class<?> root,
                         LinkedHashSet<ClassRenderRecord> documented,
                         Set<Class<?>> external) {
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

        renderToc(sb, documented);
        for (ClassRenderRecord c : documented) {
            renderClass(sb, c.theClass(), c.theClass() == root);
        }
        renderExternal(sb, external);

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * Выдать css
     * @return {String} строчка без style
     */
    protected String css() {
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

    /**
     * Table of Content
     * @param sb
     */
    private void renderToc(StringBuilder sb, LinkedHashSet<ClassRenderRecord> documented) {
        sb.append("<div class=\"card\"><h2>Содержание</h2>\n<ol class=\"toc\">\n");
        for (ClassRenderRecord c : documented) {
            sb.append("<li><a href=\"#").append(anchor(c.theClass())).append("\">")
                    .append(escape(c.theClass().getName())).append("</a> <span class=\"kind\">")
                    .append(kindOf(c.theClass())).append("</span></li>\n");
        }
        sb.append("</ol></div>\n");
    }

    private void renderExternal(StringBuilder sb, Set<Class<?>> external) {
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
        if (info != null && !info.value().isEmpty()) {
            sb.append("<p class=\"descr\">").append(escape(info.value())).append("</p>\n");
        }

        // --- общие сведения
        sb.append("<table>\n");
        String pkg = (c.getPackage() == null) ? "" : c.getPackage().getName();
        row(sb, "Пакет", escape(pkg.isEmpty() ? "(пакет по умолчанию)" : pkg));
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
        if (info != null && !info.author().isEmpty()) {
            row(sb, "Автор", escape(info.author()));
        }
        if (info != null && !info.since().isEmpty()) {
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

    private void renderFields(StringBuilder sb, ClassRenderRecord c) {
        Field[] fields = c.fields();
        sb.append("<h3>Поля</h3>\n");
        if (fields.length == 0) {
            sb.append("<p class=\"meta\">нет</p>\n");
            return;
        }
        sb.append("<table class=\"grid\">\n<tr><th>Модификаторы</th><th>Тип</th>"
                + "<th>Имя</th><th>Аннотации</th><th>Описание</th></tr>\n");
        int shown = 0;
        for (Field f : fields) {
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

    private void renderConstructors(StringBuilder sb, Class<?> c, boolean showSyntheticMethods) {
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        sb.append("<h3>Конструкторы</h3>\n");
        if (ctors.length == 0) {
            sb.append("<p class=\"meta\">нет</p>\n");
            return;
        }
        sb.append("<table class=\"grid\">\n<tr><th>Модификаторы</th><th>Параметры</th>"
                + "<th>Исключения</th><th>Аннотации</th></tr>\n");
        for (Constructor<?> ct : ctors) {
            if (skipMember(ct.getModifiers(), ct.isSynthetic(), ct.getAnnotations(), showSyntheticMethods)) {
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

    private void renderMethods(StringBuilder sb, Class<?> c, boolean showSyntheticMethods) {
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
            if (skipMember(m.getModifiers(), m.isSynthetic(), m.getAnnotations(), showSyntheticMethods)) {
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

    // Вспомогательные

    private String params(Type[] types) {
        if (types.length == 0) {
            return "<span class=\"meta\">()</span>";
        }
        return typeList(types);
    }

    protected String typeList(Type[] types) {
        List<String> parts = new ArrayList<String>();
        for (Type t : types) {
            parts.add(typeHtml(t));
        }
        return join(parts, ", ");
    }

    /** Имя типа с гиперссылками на документированные классы. */
    protected String typeHtml(Type t) {
        String text = escape(t.getTypeName());

        List<Class<?>> refs = new ArrayList<Class<?>>();
        for (Class<?> c : classesOf(t)) {
            if (documented.contains(c)) {
                refs.add(c);
            }
        }
        // длинные имена подставляем первыми, чтобы вложенные имена не ломали разметку
        refs.sort((a, b) -> b.getName().length() - a.getName().length());

        List<String> links = new ArrayList<String>();
        for (Class<?> c : refs) {
            String name = escape(c.getTypeName());
            if (!text.contains(name)) {
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
}
