package cn.subat.impl.common.util;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

public class ImplCamelToSnake extends PhysicalNamingStrategyStandardImpl {

    public static final ImplCamelToSnake INSTANCE =
            new ImplCamelToSnake();

    public static final String CAMEL_CASE_REGEX = "([a-z]+)([A-Z]+)";

    public static final String SNAKE_CASE_PATTERN = "$1\\_$2";


    public static String camelToSnake(String str){
        return str.replaceAll(
                        CAMEL_CASE_REGEX,
                        SNAKE_CASE_PATTERN)
                .toLowerCase();
    }

    @Override
    public Identifier toPhysicalCatalogName(
            Identifier name,
            JdbcEnvironment context) {
        return formatIdentifier(
                super.toPhysicalCatalogName(name, context)
        );
    }

    @Override
    public Identifier toPhysicalSchemaName(
            Identifier name,
            JdbcEnvironment context) {
        return formatIdentifier(
                super.toPhysicalSchemaName(name, context)
        );
    }

    @Override
    public Identifier toPhysicalTableName(
            Identifier name,
            JdbcEnvironment context) {
        return formatIdentifier(
                super.toPhysicalTableName(name, context)
        );
    }

    @Override
    public Identifier toPhysicalSequenceName(
            Identifier name,
            JdbcEnvironment context) {
        return formatIdentifier(
                super.toPhysicalSequenceName(name, context)
        );
    }

    @Override
    public Identifier toPhysicalColumnName(
            Identifier name,
            JdbcEnvironment context) {
        return formatIdentifier(
                super.toPhysicalColumnName(name, context)
        );
    }

    private Identifier formatIdentifier(
            Identifier identifier) {
        if (identifier != null) {
            String name = identifier.getText();

            String formattedName = name
                    .replaceAll(
                            CAMEL_CASE_REGEX,
                            SNAKE_CASE_PATTERN)
                    .toLowerCase();

            return !formattedName.equals(name) ?
                    Identifier.toIdentifier(
                            formattedName,
                            identifier.isQuoted()
                    ) :
                    identifier;
        } else {
            return null;
        }

    }
}
