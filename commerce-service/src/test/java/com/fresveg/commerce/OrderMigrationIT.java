package com.fresveg.commerce;
import static org.assertj.core.api.Assertions.*;
import com.fresveg.testing.database.DatabaseFixture;
import java.sql.*;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
class OrderMigrationIT {
 @Test void upgradeRepeatRollbackAndImmutablePrivileges()throws Exception {
  try(var pg=DatabaseFixture.container()){pg.start();DatabaseFixture.bootstrap(pg);
   try(var migration=DatabaseFixture.connect(pg,"commerce",true);var runtime=DatabaseFixture.connect(pg,"commerce",false)) {
    DatabaseFixture.migrate(migration,"database-test/commerce-phase7-baseline.yaml","commerce","service",false);
    try(var s=runtime.createStatement()){s.execute("INSERT INTO commerce.carts(cart_id,customer_id,currency) VALUES(gen_random_uuid(),gen_random_uuid(),'USD')");}
    String before=history(migration);DatabaseFixture.migrate(migration,DatabaseFixture.master("commerce"),"commerce","service",false);String after=history(migration);assertThat(after).startsWith(before);
    assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM commerce.databasechangelog")).isEqualTo("5");assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM information_schema.tables WHERE table_schema='commerce' AND table_name NOT LIKE 'databasechangelog%'")).isEqualTo("9");
    try(var s=runtime.createStatement()){s.execute("UPDATE commerce.outbox_events SET retry_count=retry_count+1");}
    for(String sql:new String[]{"UPDATE commerce.payment_attempts SET status='FAILED'","DELETE FROM commerce.refunds"})assertThatThrownBy(()->{try(var s=runtime.createStatement()){s.execute(sql);}}).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
    DatabaseFixture.migrate(migration,DatabaseFixture.master("commerce"),"commerce","service",false);assertThat(history(migration)).isEqualTo(after);
    rollback(migration);rollback(migration);rollback(migration);assertThat(history(migration)).isEqualTo(before);assertThat(DatabaseFixture.scalar(runtime,"SELECT count(*) FROM commerce.carts")).isEqualTo("1");
    DatabaseFixture.migrate(migration,DatabaseFixture.master("commerce"),"commerce","service",false);
    try(var s=runtime.createStatement()){s.execute("INSERT INTO commerce.idempotency_records(record_id,customer_id,idempotency_key,request_hash,status,plan,created_at,updated_at) VALUES(gen_random_uuid(),gen_random_uuid(),'test','hash','PREPARED','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");}
    rollback(migration);rollback(migration);assertThatThrownBy(()->rollback(migration)).hasStackTraceContaining("Order rollback refused");migration.setAutoCommit(true);
    for(String sql:new String[]{"DELETE FROM commerce.orders","UPDATE commerce.orders SET grand_total=0","UPDATE commerce.order_items SET unit_price=1","DELETE FROM commerce.order_status_history","DELETE FROM commerce.idempotency_records","UPDATE commerce.outbox_events SET payload='{}'","UPDATE commerce.outbox_events SET event_type='Other'","UPDATE commerce.databasechangelog SET md5sum=NULL"})assertThatThrownBy(()->{try(var s=runtime.createStatement()){s.execute(sql);}}).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
    assertThat(DatabaseFixture.scalar(migration,"SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class r ON r.oid=c.confrelid JOIN pg_namespace rn ON rn.oid=r.relnamespace WHERE c.contype='f' AND n.nspname='commerce' AND rn.nspname<>'commerce'")).isEqualTo("0");
   }
  }
 }
 private static String history(Connection c)throws Exception{return DatabaseFixture.scalar(c,"SELECT string_agg(id||':'||md5sum||':'||dateexecuted::text,',' ORDER BY orderexecuted) FROM commerce.databasechangelog");}
 private static void rollback(Connection c)throws Exception{var db=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));db.setDefaultSchemaName("commerce");db.setLiquibaseSchemaName("commerce");new Liquibase(DatabaseFixture.master("commerce"),new ClassLoaderResourceAccessor(),db).rollback(1,new Contexts("service"),new LabelExpression());c.setAutoCommit(true);Scope.getCurrentScope().getSingleton(liquibase.changelog.FastCheckService.class).clearCache();}
}
