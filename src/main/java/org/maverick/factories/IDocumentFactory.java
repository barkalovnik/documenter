package org.maverick.factories;

import java.util.LinkedHashSet;
import java.util.Set;

public interface IDocumentFactory {
    String render(Class<?> root,
                  LinkedHashSet<Class<?>> documented,
                  Set<Class<?>> external);
}
