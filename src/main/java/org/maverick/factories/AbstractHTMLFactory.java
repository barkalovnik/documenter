package org.maverick.factories;

import org.maverick.ClassRenderRecord;
import org.maverick.DocIgnore;
import org.maverick.DocInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public abstract class AbstractHTMLFactory implements IDocumentFactory {
    /**
     * Функция рендера
     * @param root исходный класс
     * @param documented список документированных
     * @param external список внешних документированный
     * @return возвращает готовый текст html
     */
    public abstract String render(Class<?> root,
                                  LinkedHashSet<ClassRenderRecord> documented,
                                  Set<Class<?>> external);

    /** Режет злые знаки, которые могут сломать html
     * @param s - Строка, потенциалььно содержащая злые знаки
     * @return {String} - Строка без злых знаков
     */
    protected static String escape(String s) {
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

    /**
     * Берёт имя класса и меняет символы . и $ на _ для ссылок
     * @param c класс
     * @return строку, которую можно использовать как якорь
     */
    protected String anchor(Class<?> c) {
        return c.getName().replace('.', '_').replace('$', '_');
    }

    /**
     * Возвращает вид класса для внесения в html-документ
     * @param c класс
     * @return аннотация/перечисление/класс
     */
    protected String kindOf(Class<?> c) {
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

    /**
     * По сути то toString листа, но похер - пусть будет. Соелиняет лист в строку, перечисляя его элементы
     * @param parts списисок на объединения
     * @param sep сепаратор
     * @return строку объединённую
     */
    protected static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    // Ещё вспомогательные методы

    protected void row(StringBuilder sb, String key, String value) {
        sb.append("<tr><th>").append(escape(key)).append("</th><td>")
                .append(value).append("</td></tr>\n");
    }

    /**
     *
     * @param modifiers
     * @param synthetic
     * @param annotations
     * @return
     */
    protected boolean skipMember(int modifiers,
                                 boolean synthetic,
                                 Annotation[] annotations,
                                 boolean showSyntheticMembers) {
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

    protected String annotationsHtml(Annotation[] annotations) {
        if (annotations.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<String>();
        for (Annotation a : annotations) {
            parts.add("<code>@" + escape(a.annotationType().getSimpleName()) + "</code>");
        }
        return join(parts, " ");
    }

    protected String modifiers(int mod) {
        String s = Modifier.toString(mod);
        return s.isEmpty() ? "(package-private)" : s;
    }

    /* ЭТИ МЕТОДЫ НАХУЙ СКОПИРОВАНЫ ИЗ CLASSDOCUMENTER ПОТОМУ ЧТО НАДО ПЕРЕПРОДУМАТЬ АРХИТЕКТУРУ И ЕБАНУТЬ ЧТО-ТО
    * ПОКУЛЬТУРНЕЙ СУКА НАХУЙ */

    /** Разворачивание сложного типа (дженерики, массивы, wildcard) в набор классов. */
    protected Set<Class<?>> classesOf(Type t) {
        Set<Class<?>> out = new LinkedHashSet<Class<?>>();
        classesOf(t, out);
        return out;
    }

    protected void classesOf(Type t, Set<Class<?>> out) {
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

    protected Class<?> unwrapArray(Class<?> c) {
        while (c != null && c.isArray()) {
            c = c.getComponentType();
        }
        return c;
    }
}
