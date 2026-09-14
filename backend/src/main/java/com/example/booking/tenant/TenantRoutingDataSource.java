package com.example.booking.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDataSourceManager dataSourceManager;

    public TenantRoutingDataSource(
        TenantDataSourceManager dataSourceManager,
        DataSource safeDefaultDataSource
    ) {
        this.dataSourceManager = dataSourceManager;

        /*
         * Critical
         *
         * null tenant context must NEVER res real tenant
         *
         * We use the platform DB as the safe default
         */
        setDefaultTargetDataSource(safeDefaultDataSource);

        setTargetDataSources(java.util.Map.of());

        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        /*
         * null means:
         * "there is currently no tenant context."
         *
         * AbstractRoutingDataSource will use the configured
         * default DataSource
         */

        return TenantContext.getTenantId();
    }

    Object currentLookupKeyForTest() {
        return determineCurrentLookupKey();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = TenantContext.getTenantId();

        if (tenantId == null || tenantId.isBlank()) {
            return super.determineTargetDataSource();
        }

        return dataSourceManager.getDataSource(tenantId);
    }
}
