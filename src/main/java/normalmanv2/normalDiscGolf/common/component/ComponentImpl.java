package normalmanv2.normalDiscGolf.common.component;

import normalmanv2.normalDiscGolf.api.component.Component;

public class ComponentImpl<T> implements Component<T> {

    private final T component;
    private final String id;
    private final String name;

    public ComponentImpl(T value, String id, String name) {
        this.component = value;
        this.id = id;
        this.name = name;
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
