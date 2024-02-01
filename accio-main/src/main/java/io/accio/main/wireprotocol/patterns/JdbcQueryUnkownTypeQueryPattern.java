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

package io.accio.main.wireprotocol.patterns;

import io.accio.base.Parameter;
import io.accio.base.type.PGType;

import java.util.List;
import java.util.regex.Pattern;

import static io.accio.base.type.IntegerType.INTEGER;
import static java.lang.String.format;

/**
 * Pattern for queries which JDBC used to get a local uncached type information.
 * DuckDB doesn't have record types, so we return a dummy record for main database.
 */
public class JdbcQueryUnkownTypeQueryPattern
        extends QueryWithParamPattern
{
    private final PGType<?> type;

    public JdbcQueryUnkownTypeQueryPattern(PGType<?> type)
    {
        super(Pattern.compile("(?i)^ *SELECT n\\.nspname = ANY\\(current_schemas\\(true\\)\\), n\\.nspname, t\\.typname FROM pg_catalog\\.pg_type t JOIN pg_catalog\\.pg_namespace n ON t\\.typnamespace = n\\.oid WHERE t\\.oid = \\$1",
                Pattern.CASE_INSENSITIVE), List.of(new Parameter(INTEGER, type.oid())));
        this.type = type;
    }

    @Override
    protected String rewrite(String statement)
    {
        return format("SELECT true, 'pg_catalog', '%s'", type.typName());
    }
}
