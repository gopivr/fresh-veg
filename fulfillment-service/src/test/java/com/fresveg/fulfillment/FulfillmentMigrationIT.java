package com.fresveg.fulfillment;
import static org.assertj.core.api.Assertions.*;
import com.fresveg.testing.database.*;
import java.sql.SQLException;
import java.util.UUID;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
class FulfillmentMigrationIT {
 @Test void migrationCreatesDomainTablesWithGuardedRollbackAndRestrictedRuntime() throws Exception {
  try(var pg=DatabaseFixture.container()){pg.start();DatabaseFixture.bootstrap(pg);
   try(var admin=DatabaseFixture.admin(pg);var runtime=DatabaseFixture.connect(pg,"fulfillment",false);var migration=DatabaseFixture.connect(pg,"fulfillment",true)){
    DatabaseFixture.migrate(migration,DatabaseFixture.master("fulfillment"),"fulfillment","service",false);
    assertThat(DatabaseFixture.scalar(admin,"SELECT count(*) FROM fulfillment.databasechangelog")).isEqualTo("3");
    assertThat(DatabaseFixture.scalar(admin,"SELECT count(*) FROM information_schema.tables WHERE table_schema='fulfillment' AND table_name IN ('delivery_slots','fulfillments','fulfillment_items','shipments','shipment_events','delivery_assignments')")).isEqualTo("6");
    try(var s=runtime.createStatement()){s.execute("INSERT INTO fulfillment.delivery_slots(slot_id,service_area,start_time,end_time,capacity,status) VALUES ('"+UUID.randomUUID()+"','north',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 hour',2,'OPEN')");}
    for(String sql:new String[]{"DELETE FROM fulfillment.delivery_slots","UPDATE fulfillment.fulfillments SET order_id='"+UUID.randomUUID()+"'"})assertThatThrownBy(()->{try(var s=runtime.createStatement()){s.execute(sql);}}).isInstanceOf(SQLException.class);
    var db=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(migration));db.setDefaultSchemaName("fulfillment");db.setLiquibaseSchemaName("fulfillment");var l=new Liquibase(DatabaseFixture.master("fulfillment"),new ClassLoaderResourceAccessor(),db);
    l.rollback(1,new Contexts("service"),new LabelExpression());
    assertThatThrownBy(()->l.rollback(1,new Contexts("service"),new LabelExpression())).hasMessageContaining("Fulfillment rollback refused");
   }
  }
 }
}
