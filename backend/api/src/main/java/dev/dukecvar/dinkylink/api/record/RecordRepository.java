package dev.dukecvar.dinkylink.api.record;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RecordRepository extends CrudRepository<Record, String> {
    @Query("SELECT insert_record(:urlhash, :url)")
    String insertRecord(@Param("urlhash") byte[] urlhash, @Param("url") String url);
}
