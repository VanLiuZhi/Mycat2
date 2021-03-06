/**
 * Copyright (C) <2019>  <chen junwen>
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If
 * not, see <http://www.gnu.org/licenses/>.
 */
package io.mycat.datasource.jdbc.datasource;

import io.mycat.datasource.jdbc.JdbcRuntime;
import io.mycat.datasource.jdbc.thread.GThread;
import io.mycat.logTip.MycatLogger;
import io.mycat.logTip.MycatLoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Junwen Chen
 **/
public class LocalTransactionSessionImpl implements TransactionSession {

    private static final MycatLogger LOGGER = MycatLoggerFactory
            .getLogger(LocalTransactionSessionImpl.class);

    private final GThread gthread;
    private final Map<String, DefaultConnection> connectionMap = new HashMap<>();
    private boolean autocommit = false;
    private boolean isTrancation = false;
    private int transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;

    public LocalTransactionSessionImpl(GThread gthread) {
        this.gthread = gthread;
    }

    public DefaultConnection getConnection(String jdbcDataSource) {
        beforeDoAction();
        return connectionMap.compute(jdbcDataSource,
                (dataSource, absractConnection) -> {
                    if (absractConnection == null) {
                        return JdbcRuntime.INSTANCE.getConnection(dataSource, !isTrancation, transactionIsolation);
                    } else {
                        return absractConnection;
                    }
                });
    }

    @Override
    public void reset() {
        if (isInTransaction()) {
            rollback();
        }
        afterDoAction();
    }

    @Override
    public void setTransactionIsolation(int transactionIsolation) {
        this.transactionIsolation = transactionIsolation;
    }

    @Override
    public void begin() {
        beforeDoAction();
        connectionMap.values().forEach(c -> c.close());
        connectionMap.clear();
        isTrancation = true;
    }

    @Override
    public void commit() {
        isTrancation = false;
        for (DefaultConnection value : connectionMap.values()) {
            try {
                ((DefaultConnection) value).connection.commit();
            } catch (SQLException e) {
                LOGGER.error("", e);
            }
        }
        afterDoAction();
    }

    @Override
    public void rollback() {
        isTrancation = false;
        for (DefaultConnection value : connectionMap.values()) {
            try {
                ((DefaultConnection) value).connection.rollback();
            } catch (SQLException e) {
                LOGGER.error("", e);
            }
        }
        afterDoAction();
    }

    @Override
    public boolean isInTransaction() {
        return isTrancation;
    }

    @Override
    public void beforeDoAction() {
        if (!isInTransaction()) {
            connectionMap.values().forEach(c -> c.close());
            connectionMap.clear();
        } else {
            isTrancation = true;
        }
    }

    @Override
    public void afterDoAction() {
        if (!isInTransaction()) {
            connectionMap.values().forEach(c -> c.close());
            connectionMap.clear();
        }
    }

    @Override
    public void setAutocommit(boolean autocommit) {
        this.autocommit = autocommit;
    }
}