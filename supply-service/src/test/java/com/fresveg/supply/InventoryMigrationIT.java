package com.fresveg.supply;
import static org.assertj.core.api.Assertions.*;
import com.fresveg.testing.database.DatabaseFixture;
import java.sql.*;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
class InventoryMigrationIT {
 @Test void phaseFiveUpgradeRepeatRollbackAndLedgerPrivileges() throws Exception {
  try(var postgres=DatabaseFixture.container()) {
   postgres.start();DatabaseFixture.bootstrap(postgres);
   try(var migration=DatabaseFixture.connect(postgres,"supply",true);var runtime=DatabaseFixture.connect(postgres,"supply",false)) {
    DatabaseFixture.migrate(migration,"database-test/supply-phase5-baseline.yaml","supply","service",false);
    String baseline=history(migration);
    // Preserve an existing real Phase 5 aggregate across upgrade and empty inventory rollback.
    try(var st=runtime.createStatement()) {
     st.execute("INSERT INTO supply.vendor_locations(location_id,vendor_id,code,name,line1,city,postal_code,country_code,status) VALUES ('30000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000002','UPGRADE','Farm','Road','Boston','02110','US','ACTIVE')");
     st.execute("INSERT INTO supply.vendor_listings(listing_id,vendor_id,location_id,product_id,variant_id,vendor_sku,uom_code,minimum_order_quantity,status) VALUES ('30000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001',gen_random_uuid(),gen_random_uuid(),'UPGRADE','KG',1,'ACTIVE')");
    }
    DatabaseFixture.migrate(migration,DatabaseFixture.master("supply"),"supply","service",false);
    assertThat(history(migration)).startsWith(baseline);String upgraded=history(migration);
    assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM supply.databasechangelog")).isEqualTo("4");
    assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM information_schema.tables WHERE table_schema='supply' AND table_name NOT LIKE 'databasechangelog%'")).isEqualTo("8");
    assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM information_schema.columns WHERE table_schema='supply' AND column_name IN ('created_at','updated_at') AND data_type='timestamp with time zone'")).isEqualTo("16");
    assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class r ON r.oid=c.confrelid JOIN pg_namespace rn ON rn.oid=r.relnamespace WHERE c.contype='f' AND n.nspname='supply' AND rn.nspname<>'supply'")).isEqualTo("0");
    DatabaseFixture.migrate(migration,DatabaseFixture.master("supply"),"supply","service",false);assertThat(history(migration)).isEqualTo(upgraded);
    rollback(migration);rollback(migration);assertThat(history(migration)).isEqualTo(baseline);
    assertThat(DatabaseFixture.scalar(runtime,"SELECT count(*) FROM supply.vendor_listings")).isEqualTo("1");
    DatabaseFixture.migrate(migration,DatabaseFixture.master("supply"),"supply","service",false);
    try(var st=runtime.createStatement()) { st.execute("INSERT INTO supply.inventory(inventory_id,listing_id) VALUES ('30000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000003')"); }
    String populated=history(migration);rollback(migration);assertThat(history(migration)).isNotEqualTo(populated);String afterIndexRollback=history(migration);assertThatThrownBy(()->rollback(migration)).hasStackTraceContaining("Inventory rollback refused");migration.setAutoCommit(true);assertThat(history(migration)).isEqualTo(afterIndexRollback);
    for(String sql:new String[]{"UPDATE supply.inventory_transactions SET quantity_delta=0","DELETE FROM supply.inventory_transactions","DELETE FROM supply.inventory","UPDATE supply.databasechangelog SET md5sum=NULL"}) {
     assertThatThrownBy(()->{try(var st=runtime.createStatement()){st.execute(sql);}}).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
    }
    for(String sql:new String[]{"UPDATE supply.inventory SET quantity_on_hand=-1","UPDATE supply.inventory SET reserved_quantity=-1","UPDATE supply.inventory SET reserved_quantity=1"}) {
     assertThatThrownBy(()->{try(var st=runtime.createStatement()){st.execute(sql);}}).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23514"));
    }
   }
  }
 }
 private static String history(Connection c)throws Exception {return DatabaseFixture.scalar(c,"SELECT string_agg(id||':'||filename||':'||md5sum||':'||dateexecuted::text,',' ORDER BY orderexecuted) FROM supply.databasechangelog");}
 private static void rollback(Connection c)throws Exception {
  var db=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));db.setDefaultSchemaName("supply");db.setLiquibaseSchemaName("supply");
  new Liquibase(DatabaseFixture.master("supply"),new ClassLoaderResourceAccessor(),db).rollback(1,new Contexts("service"),new LabelExpression());c.setAutoCommit(true);
  Scope.getCurrentScope().getSingleton(liquibase.changelog.FastCheckService.class).clearCache();
 }
}
