package normalmanv2.normalDiscGolf.common.component;

import normalmanv2.normalDiscGolf.api.component.Component;

public record ComponentImpl<T>(String id, String name, T component) implements Component<T> {

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
