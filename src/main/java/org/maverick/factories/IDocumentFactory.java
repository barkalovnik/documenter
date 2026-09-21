package org.maverick.factories;

import org.maverick.ClassRenderRecord;

import java.util.LinkedHashSet;
import java.util.Set;

public interface IDocumentFactory {
    String render(Class<?> root,
                  LinkedHashSet<ClassRenderRecord> documented,
                  Set<Class<?>> external);
}
