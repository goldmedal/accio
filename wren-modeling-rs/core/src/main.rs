use std::any::Any;
use std::collections::HashMap;
use std::sync::Arc;

use arrow_schema::SchemaRef;
use async_trait::async_trait;
use datafusion::catalog::schema::MemorySchemaProvider;
use datafusion::catalog::{CatalogProvider, MemoryCatalogProvider};
use datafusion::datasource::{TableProvider, TableType};
use datafusion::error::Result;
use datafusion::execution::context::SessionState;
use datafusion::logical_expr::Expr;
use datafusion::physical_plan::ExecutionPlan;
use datafusion::prelude::{CsvReadOptions, SessionContext};

use wren_core::logical_plan::rule::{ModelAnalyzeRule, ModelGenerationRule};
use wren_core::logical_plan::utils::create_schema;
use wren_core::mdl::builder::{
    ColumnBuilder, ManifestBuilder, ModelBuilder, RelationshipBuilder,
};
use wren_core::mdl::manifest::{JoinType, Model};
use wren_core::mdl::{AnalyzedWrenMDL, WrenMDL};

#[tokio::main]
async fn main() -> Result<()> {
    let manifest = ManifestBuilder::new()
        .model(
            ModelBuilder::new("customers")
                .table_reference("customers")
                .column(ColumnBuilder::new("city", "varchar").build())
                .column(ColumnBuilder::new("id", "varchar").build())
                .column(ColumnBuilder::new("state", "varchar").build())
                .primary_key("id")
                .build(),
        )
        .model(
            ModelBuilder::new("order_items")
                .table_reference("order_items")
                .column(ColumnBuilder::new("freight_value", "double").build())
                .column(ColumnBuilder::new("id", "varchar").build())
                .column(ColumnBuilder::new("item_number", "integer").build())
                .column(ColumnBuilder::new("order_id", "varchar").build())
                .column(ColumnBuilder::new("price", "double").build())
                .column(ColumnBuilder::new("product_id", "varchar").build())
                .column(ColumnBuilder::new("shipping_limit_date", "varchar").build())
                .primary_key("id")
                .build(),
        )
        .model(
            ModelBuilder::new("orders")
                .table_reference("orders")
                .column(ColumnBuilder::new("approved_timestamp", "varchar").build())
                .column(ColumnBuilder::new("customer_id", "varchar").build())
                .column(ColumnBuilder::new("delivered_carrier_date", "varchar").build())
                .column(ColumnBuilder::new("estimated_delivery_date", "varchar").build())
                .column(ColumnBuilder::new("order_id", "varchar").build())
                .column(ColumnBuilder::new("purchase_timestamp", "varchar").build())
                .column(
                    ColumnBuilder::new("customer", "customers")
                        .relationship("orders_customer")
                        .build(),
                )
                .column(
                    ColumnBuilder::new("customer_state", "varchar")
                        .calculated(true)
                        .expression("customer.state")
                        .build(),
                )
                .build(),
        )
        .relationship(
            RelationshipBuilder::new("orders_customer")
                .model("orders")
                .model("customers")
                .join_type(JoinType::ManyToOne)
                .condition("orders.customer_id = customers.id")
                .build(),
        )
        .build();

    // register the table
    let ctx = SessionContext::new();
    ctx.register_csv(
        "orders",
        "sqllogictest/tests/resources/ecommerce/orders.csv",
        CsvReadOptions::new(),
    )
    .await?;
    let provider = ctx
        .catalog("datafusion")
        .unwrap()
        .schema("public")
        .unwrap()
        .table("orders")
        .await?
        .unwrap();
    let register = HashMap::from([("orders".to_string(), provider)]);
    let analyzed_mdl = Arc::new(AnalyzedWrenMDL::analyze_with_tables(manifest, register));

    let new_state = ctx
        .state()
        .add_analyzer_rule(Arc::new(ModelAnalyzeRule::new(Arc::clone(&analyzed_mdl))))
        .add_analyzer_rule(Arc::new(ModelGenerationRule::new(Arc::clone(
            &analyzed_mdl,
        ))));
    register_table_with_mdl(&ctx, Arc::clone(&analyzed_mdl.wren_mdl)).await;
    let new_ctx = SessionContext::new_with_state(new_state);
    // create a plan to run a SQL query
    let df = new_ctx.sql("SELECT * FROM wrenai.default.orders").await?;

    // execute and print results
    df.show().await?;
    Ok(())
}

pub async fn register_table_with_mdl(ctx: &SessionContext, wren_mdl: Arc<WrenMDL>) {
    let catalog = MemoryCatalogProvider::new();
    let schema = MemorySchemaProvider::new();

    catalog
        .register_schema(&wren_mdl.manifest.schema, Arc::new(schema))
        .unwrap();
    ctx.register_catalog(&wren_mdl.manifest.catalog, Arc::new(catalog));

    for model in wren_mdl.manifest.models.iter() {
        let table = WrenDataSource::new(Arc::clone(model));
        ctx.register_table(
            format!(
                "{}.{}.{}",
                &wren_mdl.manifest.catalog, &wren_mdl.manifest.schema, &model.name
            ),
            Arc::new(table),
        )
        .unwrap();
    }
}

struct WrenDataSource {
    schema: SchemaRef,
}

impl WrenDataSource {
    pub fn new(model: Arc<Model>) -> Self {
        let schema = create_schema(model.columns.clone());
        Self { schema }
    }
}

#[async_trait]
impl TableProvider for WrenDataSource {
    fn as_any(&self) -> &dyn Any {
        self
    }

    fn schema(&self) -> SchemaRef {
        self.schema.clone()
    }

    fn table_type(&self) -> TableType {
        TableType::View
    }

    async fn scan(
        &self,
        _state: &SessionState,
        _projection: Option<&Vec<usize>>,
        // filters and limit can be used here to inject some push-down operations if needed
        _filters: &[Expr],
        _limit: Option<usize>,
    ) -> Result<Arc<dyn ExecutionPlan>> {
        unreachable!("WrenDataSource should be replaced before physical planning")
    }
}
