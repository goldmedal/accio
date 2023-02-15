/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.graphmdl.main.connector.bigquery;

import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.common.collect.ImmutableMap;
import io.graphmdl.spi.GraphMDLException;
import io.graphmdl.spi.type.DateType;
import io.graphmdl.spi.type.PGType;
import io.graphmdl.spi.type.TimestampType;

import java.util.Map;
import java.util.Optional;

import static com.google.cloud.bigquery.StandardSQLTypeName.BOOL;
import static com.google.cloud.bigquery.StandardSQLTypeName.BYTES;
import static com.google.cloud.bigquery.StandardSQLTypeName.DATE;
import static com.google.cloud.bigquery.StandardSQLTypeName.FLOAT64;
import static com.google.cloud.bigquery.StandardSQLTypeName.INT64;
import static com.google.cloud.bigquery.StandardSQLTypeName.STRING;
import static com.google.cloud.bigquery.StandardSQLTypeName.TIMESTAMP;
import static io.graphmdl.spi.metadata.StandardErrorCode.NOT_SUPPORTED;
import static io.graphmdl.spi.type.BigIntType.BIGINT;
import static io.graphmdl.spi.type.BooleanType.BOOLEAN;
import static io.graphmdl.spi.type.ByteaType.BYTEA;
import static io.graphmdl.spi.type.DoubleType.DOUBLE;
import static io.graphmdl.spi.type.IntegerType.INTEGER;
import static io.graphmdl.spi.type.RealType.REAL;
import static io.graphmdl.spi.type.SmallIntType.SMALLINT;
import static io.graphmdl.spi.type.TinyIntType.TINYINT;
import static io.graphmdl.spi.type.VarcharType.VARCHAR;

public final class BigQueryType
{
    private BigQueryType() {}

    private static final Map<StandardSQLTypeName, PGType<?>> bqTypeToPgTypeMap;
    private static final Map<PGType<?>, StandardSQLTypeName> pgTypeToBqTypeMap;

    static {
        bqTypeToPgTypeMap = ImmutableMap.<StandardSQLTypeName, PGType<?>>builder()
                .put(BOOL, BOOLEAN)
                .put(INT64, BIGINT)
                .put(STRING, VARCHAR)
                .put(FLOAT64, DOUBLE)
                .put(DATE, DateType.DATE)
                .put(BYTES, BYTEA)
                .put(TIMESTAMP, TimestampType.TIMESTAMP)
                .build();

        pgTypeToBqTypeMap = ImmutableMap.<PGType<?>, StandardSQLTypeName>builder()
                .put(BOOLEAN, BOOL)
                .put(BIGINT, INT64)
                .put(INTEGER, INT64)
                .put(SMALLINT, INT64)
                .put(TINYINT, INT64)
                .put(VARCHAR, STRING)
                .put(DOUBLE, FLOAT64)
                .put(REAL, FLOAT64)
                .put(DateType.DATE, DATE)
                .put(BYTEA, BYTES)
                .build();
    }

    public static PGType<?> toPGType(StandardSQLTypeName bigQueryType)
    {
        return Optional.ofNullable(bqTypeToPgTypeMap.get(bigQueryType))
                .orElseThrow(() -> new GraphMDLException(NOT_SUPPORTED, "Unsupported Type: " + bigQueryType));
    }

    public static StandardSQLTypeName toBqType(PGType<?> pgType)
    {
        return Optional.ofNullable(pgTypeToBqTypeMap.get(pgType))
                .orElseThrow(() -> new GraphMDLException(NOT_SUPPORTED, "Unsupported Type: " + pgType.typName()));
    }
}