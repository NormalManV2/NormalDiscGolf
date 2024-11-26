package normalmanv2.normalDiscGolf.common.component;

import normalmanv2.normalDiscGolf.api.component.Component;

public class ComponentImpl<T> implements Component<T> {

    private final String id;
    private final String name;
    private final T component;

    public ComponentImpl(String id, String name, T component) {
        this.id = id;
        this.name = name;
        this.component = component;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public T get() {
        return this.component;
    }
}
