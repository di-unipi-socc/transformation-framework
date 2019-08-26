package io.github.edmm.model.component;

import io.github.edmm.core.parser.MappingEntity;
import io.github.edmm.model.visitor.ComponentVisitor;
import lombok.ToString;

@ToString
public class WebApplication extends RootComponent {

    public WebApplication(MappingEntity mappingEntity) {
        super(mappingEntity);
    }

    @Override
    public void accept(ComponentVisitor v) {
        v.visit(this);
    }
}
