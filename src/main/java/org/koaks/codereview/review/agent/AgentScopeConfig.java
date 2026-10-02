package org.koaks.codereview.review.agent;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class AgentScopeConfig {

    static final String SESSION_TABLE = "agentscope_sessions";

    /** Agent conversation state lives next to the business tables; AgentScope creates its own table. */
    @Bean
    public AgentStateStore agentStateStore(DataSource dataSource) throws SQLException {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getCatalog();
        }
        return new MysqlAgentStateStore(dataSource, database, SESSION_TABLE, true);
    }

    @Bean
    public ReviewTools reviewTools() {
        return new ReviewTools();
    }
}
