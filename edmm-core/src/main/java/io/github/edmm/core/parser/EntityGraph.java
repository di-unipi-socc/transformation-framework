package io.github.edmm.core.parser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.edmm.core.parser.support.GraphNormalizer;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Represents the content of a template as a graph.
 */
public class EntityGraph extends SimpleDirectedGraph<Entity, EntityGraph.Edge> {

    public static final EntityId ROOT = new EntityId("0");
    public static final EntityId COMPONENTS = ROOT.extend("components");
    public static final EntityId COMPONENT_TYPES = ROOT.extend("component_types");
    public static final EntityId RELATION_TYPES = ROOT.extend("relation_types");

    public EntityGraph(InputStream is) {
        super((source, target) -> new Edge(target.getName(), source, target));
        addVertex(new MappingEntity(ROOT, this));
        Node snakeNode = new Yaml().compose(new InputStreamReader(is));
        populateGraph(snakeNode, ROOT);
        GraphNormalizer.normalize(this);
    }

    public Optional<Entity> getEntity(EntityId id) {
        return this.vertexSet().stream()
                .filter(entity -> id.equals(entity.getId()))
                .findFirst();
    }

    public Optional<Entity> getEntity(List<String> path) {
        return getEntity(new EntityId(path));
    }

    public void addEntity(Entity entity) {
        if (!entity.getParent().isPresent()) {
            return;
        }
        Entity parent = entity.getParent().get();
        boolean added = addVertex(entity);
        if (added) {
            addEdge(parent, entity);
        }
    }

    public void removeEntity(Entity entity) {
        // Remove edges from and to this entity
        Stream<Edge> incomingEdges = incomingEdgesOf(entity).stream();
        Stream<Edge> outgoingEdges = outgoingEdgesOf(entity).stream();
        Stream.concat(incomingEdges, outgoingEdges).forEach(this::removeEdge);
        // Remove vertex
        removeVertex(entity);
    }

    public void replaceEntity(Entity source, Entity target) {
        // Collect incoming and outgoing edges of the source
        Set<Pair<Entity, String>> predecessors = incomingEdgesOf(source).stream()
                .map(c -> Pair.of(c.getSource(), c.getName())).collect(Collectors.toSet());
        Set<Pair<Entity, String>> successors = outgoingEdgesOf(source).stream()
                .map(c -> Pair.of(c.getTarget(), c.getName())).collect(Collectors.toSet());
        removeVertex(source);
        addVertex(target);
        // Redirect existing edges to target entity
        predecessors.forEach(p -> addEdge(p.getLeft(), target, new Edge(p.getRight(), p.getLeft(), target)));
        successors.forEach(p -> addEdge(target, p.getLeft(), new Edge(p.getRight(), target, p.getLeft())));
    }

    public Set<Entity> getChildren(EntityId id) {
        Optional<Entity> entity = getEntity(id);
        if (entity.isPresent()) {
            return entity.get().getChildren();
        } else {
            return Collections.emptySet();
        }
    }

    public void addEdge(Entity source, Entity target, String name) {
        addEdge(source, target, new Edge(name, source, target));
    }

    private void populateGraph(Node node, EntityId id) {
        if (node instanceof MappingNode) {
            MappingNode mappingNode = (MappingNode) node;
            MappingEntity mappingEntity = new MappingEntity(id, this);
            addEntity(mappingEntity);
            for (NodeTuple tuple : mappingNode.getValue()) {
                String key = ((ScalarNode) tuple.getKeyNode()).getValue();
                Node childNode = tuple.getValueNode();
                EntityId childId = id.extend(key);
                populateGraph(childNode, childId);
            }
        } else if (node instanceof SequenceNode) {
            SequenceNode sequenceNode = (SequenceNode) node;
            SequenceEntity sequenceEntity = new SequenceEntity(id, this);
            addEntity(sequenceEntity);
            for (int i = 0; i < sequenceNode.getValue().size(); i++) {
                Node childNode = sequenceNode.getValue().get(i);
                String childName;
                if (childNode instanceof MappingNode) {
                    NodeTuple childTuple = ((MappingNode) childNode).getValue().get(0);
                    childName = ((ScalarNode) childTuple.getKeyNode()).getValue();
                    childNode = childTuple.getValueNode();
                } else {
                    childName = String.valueOf(i);
                }
                EntityId childId = id.extend(childName);
                populateGraph(childNode, childId);
            }
        } else if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            String value = scalarNode.getValue();
            if (Stream.of("~", "null").collect(Collectors.toSet()).contains(value)) {
                value = null;
            }
            ScalarEntity scalarEntity = new ScalarEntity(value, id, this);
            addEntity(scalarEntity);
        }
    }

    @Getter
    public static class Edge {

        private final String name;
        private final Entity source;
        private final Entity target;

        public Edge(@NonNull String name, @NonNull Entity source, @NonNull Entity target) {
            this.name = name;
            this.source = source;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return Objects.equals(source, edge.source) &&
                    Objects.equals(target, edge.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target);
        }

        @Override
        public String toString() {
            return String.format("%s =%s=> %s", source.getId(), name, target.getId());
        }
    }
}