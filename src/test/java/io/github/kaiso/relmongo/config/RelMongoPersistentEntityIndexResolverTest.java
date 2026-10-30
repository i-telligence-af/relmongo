package io.github.kaiso.relmongo.config;

import io.github.kaiso.relmongo.annotation.FetchType;
import io.github.kaiso.relmongo.annotation.JoinProperty;
import io.github.kaiso.relmongo.annotation.Nested;
import io.github.kaiso.relmongo.annotation.OneToOne;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver.IndexDefinitionHolder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelMongoPersistentEntityIndexResolverTest {

    private static Set<String> indexPaths;

    @BeforeAll
    static void resolve() {
        RelMongoMappingContext mappingContext = new RelMongoMappingContext();
        mappingContext.afterPropertiesSet();

        List<IndexDefinitionHolder> indexes = new RelMongoPersistentEntityIndexResolver(mappingContext)
            .resolveIndexForEntity(mappingContext.getRequiredPersistentEntity(Root.class));

        indexPaths = indexes.stream().map(IndexDefinitionHolder::getPath).collect(Collectors.toSet());
    }

    @Test
    void resolvesIndexedTopLevelField() {
        assertEquals(true, indexPaths.contains("topLevel"));
    }

    @Test
    void resolvesIndexedFieldInNested() {
        assertEquals(true, indexPaths.contains("nested.inNested"));
    }

    @Test
    void resolvesIndexedFieldInNestedInNested() {
        assertEquals(true, indexPaths.contains("nested.deeper.inDeeper"));
    }

    @Test
    void doesNotResolveIndexesOfAssociations() {
        assertEquals(Set.of("topLevel", "nested.inNested", "nested.deeper.inDeeper"), indexPaths);
    }

    @Document
    static class Root {

        @Id
        private String id;

        @Indexed
        private String topLevel;

        @Nested
        private NestedType nested;

        @OneToOne(fetch = FetchType.LAZY)
        @JoinProperty(name = "reference")
        private Referenced reference;
    }

    static class NestedType {

        @Indexed
        private String inNested;

        @Nested
        private DeeperType deeper;

        @OneToOne(fetch = FetchType.LAZY)
        @JoinProperty(name = "nestedReference")
        private Referenced nestedReference;
    }

    static class DeeperType {

        @Indexed
        private String inDeeper;
    }

    @Document
    static class Referenced {

        @Id
        private String id;

        @Indexed
        private String inReferenced;
    }
}
