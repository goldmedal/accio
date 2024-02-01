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

package io.accio.base.sqlrewrite;

import com.google.common.collect.ImmutableList;
import io.accio.base.AccioMDL;
import io.accio.base.AnalyzedMDL;
import io.accio.base.SessionContext;
import io.accio.base.dto.CumulativeMetric;
import io.accio.base.dto.DateSpine;
import io.accio.base.dto.Manifest;
import io.accio.base.dto.Metric;
import io.accio.base.dto.Model;
import io.accio.base.dto.TimeUnit;
import org.testng.annotations.Test;

import java.util.List;

import static io.accio.base.AccioTypes.DATE;
import static io.accio.base.AccioTypes.INTEGER;
import static io.accio.base.AccioTypes.VARCHAR;
import static io.accio.base.dto.Column.column;
import static io.accio.base.dto.CumulativeMetric.cumulativeMetric;
import static io.accio.base.dto.Measure.measure;
import static io.accio.base.dto.Metric.metric;
import static io.accio.base.dto.Model.model;
import static io.accio.base.dto.Model.onBaseObject;
import static io.accio.base.dto.Window.window;
import static io.accio.base.sqlrewrite.AccioSqlRewrite.ACCIO_SQL_REWRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestCumulativeMetric
        extends AbstractTestFramework
{
    private final Manifest manifest;
    private final AccioMDL accioMDL;

    public TestCumulativeMetric()
    {
        manifest = withDefaultCatalogSchema()
                .setModels(List.of(
                        model("Orders",
                                "select * from main.orders",
                                List.of(
                                        column("orderkey", INTEGER, null, true),
                                        column("custkey", INTEGER, null, true),
                                        column("orderstatus", VARCHAR, null, true),
                                        column("totalprice", INTEGER, null, true),
                                        column("orderdate", DATE, null, true),
                                        column("orderpriority", VARCHAR, null, true),
                                        column("clerk", VARCHAR, null, true),
                                        column("shippriority", INTEGER, null, true),
                                        column("comment", VARCHAR, null, true)))))
                .setCumulativeMetrics(List.of(
                        cumulativeMetric("DailyRevenue",
                                "Orders", measure("totalprice", INTEGER, "sum", "totalprice"),
                                window("orderdate", "orderdate", TimeUnit.DAY, "1994-01-01", "1994-12-31")),
                        cumulativeMetric("WeeklyRevenue",
                                "Orders", measure("totalprice", INTEGER, "sum", "totalprice"),
                                window("orderdate", "orderdate", TimeUnit.WEEK, "1994-01-01", "1994-12-31")),
                        cumulativeMetric("MonthlyRevenue",
                                "Orders", measure("totalprice", INTEGER, "sum", "totalprice"),
                                window("orderdate", "orderdate", TimeUnit.MONTH, "1994-01-01", "1994-12-31")),
                        cumulativeMetric("QuarterlyRevenue",
                                "Orders", measure("totalprice", INTEGER, "sum", "totalprice"),
                                window("orderdate", "orderdate", TimeUnit.QUARTER, "1994-01-01", "1995-12-31")),
                        cumulativeMetric("YearlyRevenue",
                                "Orders", measure("totalprice", INTEGER, "sum", "totalprice"),
                                window("orderdate", "orderdate", TimeUnit.YEAR, "1994-01-01", "1998-12-31"))))
                .setDateSpine(new DateSpine(TimeUnit.DAY, "1970-01-01", "2077-12-31", null))
                .build();
        accioMDL = AccioMDL.fromManifest(manifest);
    }

    @Override
    protected void prepareData()
    {
        String orders = getClass().getClassLoader().getResource("tiny-orders.parquet").getPath();
        exec("create table orders as select * from '" + orders + "'");
    }

    @Test
    public void testCumulativeMetric()
    {
        List.of(true, false).forEach(enableDynamic -> {
            assertThat(query(rewrite("select * from DailyRevenue", accioMDL, enableDynamic)).size()).isEqualTo(365);
            assertThat(query(rewrite("select * from WeeklyRevenue", accioMDL, enableDynamic)).size()).isEqualTo(53);
            assertThat(query(rewrite("select * from MonthlyRevenue", accioMDL, enableDynamic)).size()).isEqualTo(12);
            assertThat(query(rewrite("select * from QuarterlyRevenue", accioMDL, enableDynamic)).size()).isEqualTo(8);
            assertThat(query(rewrite("select * from YearlyRevenue", accioMDL, enableDynamic)).size()).isEqualTo(5);
        });

        List.of(true, false).forEach(enableDynamic -> {
            assertThatCode(() -> query(rewrite("SELECT 1 FROM DailyRevenue", accioMDL, true)))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    public void testModelOnCumulativeMetric()
    {
        List<Model> models = ImmutableList.<Model>builder()
                .addAll(manifest.getModels())
                .add(onBaseObject(
                        "testModelOnCumulativeMetric",
                        "WeeklyRevenue",
                        List.of(
                                column("totalprice", INTEGER, null, false),
                                column("orderdate", "DATE", null, false)),
                        "orderdate"))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(
                copyOf(manifest)
                        .setModels(models)
                        .build());

        List.of(true, false).forEach(enableDynamic -> {
            List<List<Object>> result = query(rewrite("select * from testModelOnCumulativeMetric", mdl, enableDynamic));
            assertThat(result.get(0).size()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(53);
        });

        List.of(true, false).forEach(enableDynamic -> {
            assertThatCode(() -> query(rewrite("SELECT 1 FROM testModelOnCumulativeMetric", mdl, true)))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    public void testMetricOnCumulativeMetric()
    {
        List<Metric> metrics = ImmutableList.<Metric>builder()
                .addAll(manifest.getMetrics())
                .add(metric(
                        "testMetricOnCumulativeMetric",
                        "DailyRevenue",
                        List.of(column("ordermonth", "DATE", null, false, "date_trunc('month', orderdate)")),
                        List.of(column("totalprice", INTEGER, null, false, "sum(totalprice)")),
                        List.of()))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(
                copyOf(manifest)
                        .setMetrics(metrics)
                        .build());

        List.of(true, false).forEach(enableDynamic -> {
            List<List<Object>> result = query(rewrite("SELECT * FROM testMetricOnCumulativeMetric ORDER BY ordermonth", mdl, enableDynamic));
            assertThat(result.get(0).size()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(12);
        });

        List.of(true, false).forEach(enableDynamic -> {
            assertThatCode(() -> query(rewrite("SELECT 1 FROM testMetricOnCumulativeMetric", mdl, true)))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    public void testCumulativeMetricOnCumulativeMetric()
    {
        List<CumulativeMetric> cumulativeMetrics = ImmutableList.<CumulativeMetric>builder()
                .addAll(manifest.getCumulativeMetrics())
                .add(cumulativeMetric("testCumulativeMetricOnCumulativeMetric",
                        "YearlyRevenue", measure("totalprice", INTEGER, "sum", "totalprice"),
                        window("orderyear", "orderdate", TimeUnit.YEAR, "1994-01-01", "1998-12-31")))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(
                copyOf(manifest)
                        .setCumulativeMetrics(cumulativeMetrics)
                        .build());

        List.of(true, false).forEach(enableDynamic -> {
            List<List<Object>> result = query(rewrite("SELECT * FROM testCumulativeMetricOnCumulativeMetric ORDER BY orderyear", mdl, enableDynamic));
            assertThat(result.get(0).size()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(5);
        });

        List.of(true, false).forEach(enableDynamic -> {
            assertThatCode(() -> query(rewrite("SELECT 1 FROM testCumulativeMetricOnCumulativeMetric", mdl, true)))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    public void testInvalidCumulativeMetricOnCumulativeMetric()
    {
        List<CumulativeMetric> cumulativeMetrics = ImmutableList.<CumulativeMetric>builder()
                .addAll(manifest.getCumulativeMetrics())
                .add(cumulativeMetric("testInvalidCumulativeMetricOnCumulativeMetric",
                        "YearlyRevenue", measure("totalprice", INTEGER, "sum", "totalprice"),
                        // window refColumn is a measure that belongs to cumulative metric
                        window("foo", "totalprice", TimeUnit.YEAR, "1994-01-01", "1998-12-31")))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(
                copyOf(manifest)
                        .setCumulativeMetrics(cumulativeMetrics)
                        .build());

        List.of(true, false).forEach(enableDynamic -> {
            assertThatThrownBy(() -> rewrite("SELECT * FROM testInvalidCumulativeMetricOnCumulativeMetric", mdl, enableDynamic))
                    .hasMessage("CumulativeMetric measure cannot be window as it is not date/timestamp type");
        });
    }

    @Test
    public void testCumulativeMetricOnMetric()
    {
        List<Metric> metrics = ImmutableList.of(
                metric("RevenueByOrderdate", "Orders",
                        List.of(column("orderdate", DATE, null, true, "orderdate")),
                        List.of(column("totalprice", INTEGER, null, true, "sum(totalprice)")),
                        List.of()));
        List<CumulativeMetric> cumulativeMetrics = ImmutableList.<CumulativeMetric>builder()
                .addAll(manifest.getCumulativeMetrics())
                .add(cumulativeMetric("testCumulativeMetricOnMetric",
                        "RevenueByOrderdate", measure("totalprice", INTEGER, "sum", "totalprice"),
                        window("orderyear", "orderdate", TimeUnit.YEAR, "1994-01-01", "1998-12-31")))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(
                copyOf(manifest)
                        .setMetrics(metrics)
                        .setCumulativeMetrics(cumulativeMetrics)
                        .build());

        List.of(true, false).forEach(enableDynamic -> {
            List<List<Object>> result = query(rewrite("SELECT * FROM testCumulativeMetricOnMetric ORDER BY orderyear", mdl, enableDynamic));
            assertThat(result.get(0).size()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(5);
        });

        List.of(true, false).forEach(enableDynamic -> {
            assertThatCode(() -> query(rewrite("SELECT 1 FROM testCumulativeMetricOnMetric", mdl, true)))
                    .doesNotThrowAnyException();
        });
    }

    private String rewrite(String sql, AccioMDL accioMDL, boolean enableDynamic)
    {
        SessionContext sessionContext = SessionContext.builder()
                .setCatalog("accio")
                .setSchema("test")
                .setEnableDynamic(enableDynamic)
                .build();
        return AccioPlanner.rewrite(sql, sessionContext, new AnalyzedMDL(accioMDL), List.of(ACCIO_SQL_REWRITE));
    }
}