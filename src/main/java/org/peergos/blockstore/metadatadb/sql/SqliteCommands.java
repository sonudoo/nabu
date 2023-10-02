package org.peergos.blockstore.metadatadb.sql;

public class SqliteCommands implements SqlSupplier {

    @Override
    public String vacuumCommand() {
        return "VACUUM;";
    }

    @Override
    public String addMetadataCommand() {
        return "INSERT OR IGNORE INTO blockmetadata (cid, size, links) VALUES(?, ?, ?);";
    }

    @Override
    public String getByteArrayType() {
        return "blob";
    }

    @Override
    public String sqlInteger() {
        return "INTEGER";
    }
}
