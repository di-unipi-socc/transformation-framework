package io.github.edmm.model.component;

import java.util.Optional;

import io.github.edmm.core.parser.MappingEntity;
import io.github.edmm.model.support.Attribute;
import io.github.edmm.model.visitor.ComponentVisitor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
public class WebServer extends SoftwareComponent {

    public static Attribute<Integer> PORT = new Attribute<>("port", Integer.class);

    public WebServer(MappingEntity mappingEntity) {
        super(mappingEntity);
    }

    public Optional<Integer> getPort() {
        return getProperty(PORT);
    }

    @Override
    public void accept(ComponentVisitor v) {
        v.visit(this);
    }
}