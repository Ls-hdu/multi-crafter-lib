package MultiCraft;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.geom.Vec2;

import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.gen.Iconc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.logic.Senseable;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.heat.HeatConductor;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.draw.DrawBlock;
import mindustry.world.meta.*;

import static mindustry.Vars.content;
import static mindustry.Vars.tilesize;

public class MultiCrafter extends HeatCrafter {
    public Seq<Recipe> recipes = new Seq<>();
    public float updateEffectChance = 0.05f;
    public Effect updateEffect = Fx.none;
    public boolean showWarmup = false;

    public boolean hasPayloads = false;
    public int payloadCapacity = 1;
    public float payloadSpeed = 0.7f;
    public float payloadRotateSpeed = 5f;
    public Recipe[] recipe;
    protected boolean hasItemOutput = false;
    protected boolean hasLiquidOutput = false;

    public MultiCrafter(String name) {
        super(name);
        heatRequirement = 0f;
        configurable = true;
        hasItems = true;
        hasLiquids = true;
        saveConfig = true;
        update = true;
        solid = true;
        group = BlockGroup.none;
        buildType = MultiCrafterBuild::new;
        maxEfficiency = 4f;
        acceptsPayload = true;
        outputsPayload = true;

        config(Integer.class, (MultiCrafterBuild build, Integer value) -> {
            int newVal = Mathf.clamp(value, 0, recipes.size);
            if (build.selectRecipe != newVal) {
                build.selectRecipe = newVal;
                build.progress = 0f;
            }
        });
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                          float craftTime) {
        recipes.add(new Recipe(inputItems, inputLiquids, inputPower, inputHeat,
                outputItems, outputLiquids, outputPower, outputHeat, craftTime));
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower,
                          float craftTime) {
        addRecipe(inputItems, inputLiquids, inputPower, 0f,
                outputItems, outputLiquids, outputPower, 0f, craftTime);
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                          float craftTime, PayloadStack[] inputPayloads, PayloadStack[] outputPayloads, @Nullable DrawBlock visual) {
        recipes.add(new Recipe(inputItems, inputLiquids, inputPower, inputHeat,
                outputItems, outputLiquids, outputPower, outputHeat, craftTime,
                inputPayloads, outputPayloads, visual));
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower,
                          float craftTime, PayloadStack[] inputPayloads, PayloadStack[] outputPayloads) {
        addRecipe(inputItems, inputLiquids, inputPower, 0f,
                outputItems, outputLiquids, outputPower, 0f, craftTime,
                inputPayloads, outputPayloads);
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                          float craftTime, PayloadStack[] inputPayloads, PayloadStack[] outputPayloads) {
        recipes.add(new Recipe(inputItems, inputLiquids, inputPower, inputHeat,
                outputItems, outputLiquids, outputPower, outputHeat, craftTime,
                inputPayloads, outputPayloads, null));
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                          float craftTime, @Nullable DrawBlock visual) {
        recipes.add(new Recipe(inputItems, inputLiquids, inputPower, inputHeat,
                outputItems, outputLiquids, outputPower, outputHeat, craftTime, visual));
    }

    public void addRecipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower,
                          ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower,
                          float craftTime, @Nullable DrawBlock visual) {
        addRecipe(inputItems, inputLiquids, inputPower, 0f,
                outputItems, outputLiquids, outputPower, 0f, craftTime, visual);
    }

    private PayloadStack[] parsePayloadStacks(String[] strs) {
        if (strs == null) return new PayloadStack[0];
        java.util.ArrayList<PayloadStack> list = new java.util.ArrayList<>();
        for (String s : strs) {
            String[] parts = s.split("/");
            if (parts.length != 2) continue;
            UnlockableContent content = Vars.content.getByName(ContentType.block, parts[0]);
            if (content == null) content = Vars.content.getByName(ContentType.unit, parts[0]);
            if (content != null) {
                int amount = Integer.parseInt(parts[1]);
                list.add(new PayloadStack(content, amount));
            }
        }
        return list.toArray(new PayloadStack[0]);
    }

    @Override
    public void init() {

        if (recipe != null && recipe.length > 0) {
            for (Recipe r : recipe) {
                r.ensureArrays();
                if (r.inputPayloads == null) r.inputPayloads = new String[0];
                if (r.outputPayloads == null) r.outputPayloads = new String[0];
                r.cachedInputPayloads = parsePayloadStacks(r.inputPayloads);
                r.cachedOutputPayloads = parsePayloadStacks(r.outputPayloads);
                recipes.add(r);
            }
            recipe = null;
        }


        for (Recipe rec : recipes) {
            rec.ensureArrays();
            if (rec.cachedInputPayloads == null && rec.inputPayloads != null) {
                rec.cachedInputPayloads = parsePayloadStacks(rec.inputPayloads);
            }
            if (rec.cachedOutputPayloads == null && rec.outputPayloads != null) {
                rec.cachedOutputPayloads = parsePayloadStacks(rec.outputPayloads);
            }
            if (rec.cachedInputPayloads == null) rec.cachedInputPayloads = new PayloadStack[0];
            if (rec.cachedOutputPayloads == null) rec.cachedOutputPayloads = new PayloadStack[0];

            if (rec.cachedInputPayloads.length > 0) hasPayloads = true;
            if (rec.cachedOutputPayloads.length > 0) hasPayloads = true;
        }


        ObjectSet<Item> allOutputItems = new ObjectSet<>();
        ObjectSet<Liquid> allOutputLiquids = new ObjectSet<>();

        for (Recipe rec : recipes) {
            for (ItemStack s : rec.outputItems) {
                if (s != null && s.item != null) allOutputItems.add(s.item);
            }
            for (LiquidStack s : rec.outputLiquids) {
                if (s != null && s.liquid != null) allOutputLiquids.add(s.liquid);
            }
        }

        if (allOutputItems.size > 0) {
            outputItems = new ItemStack[allOutputItems.size];
            int i = 0;
            for (Item item : allOutputItems) outputItems[i++] = new ItemStack(item, 1);
        }

        if (allOutputLiquids.size > 0) {
            outputLiquids = new LiquidStack[allOutputLiquids.size];
            int i = 0;
            for (Liquid liq : allOutputLiquids) outputLiquids[i++] = new LiquidStack(liq, 0.1f);
        }

        hasItemOutput = outputItems != null;
        hasLiquidOutput = outputLiquids != null;





        boolean hasPowerInput = recipes.contains(r -> r.inputPower > 0);
        if (hasPowerInput) {
            consume(new ConsumePower(0f, 0f, false) {
                @Override
                public float requestedPower(Building entity) {
                    if (entity instanceof MultiCrafterBuild build) {
                        Recipe rec = build.getCurrentRecipe();
                        if (rec != null && rec.inputPower > 0) return rec.inputPower;
                    }
                    return 0;
                }

                @Override
                public float efficiency(Building build) {
                    if (build instanceof MultiCrafterBuild multi) {
                        Recipe rec = multi.getCurrentRecipe();
                        if (rec != null && rec.inputPower > 0) {
                            return build.power != null ? build.power.status : 0f;
                        }
                        return 1f;
                    }
                    return super.efficiency(build);
                }
            });
        }

        for (Recipe r : recipes) {
            if (r.visual != null) {
                r.visual.load(this);
            }
        }



        super.init();
        outputsLiquid = hasLiquidOutput;
        if (outputsLiquid && (liquidOutputDirections == null || liquidOutputDirections.length == 0)) {
            liquidOutputDirections = new int[]{-1};
        }
    }

    @Override
    public boolean outputsItems() {
        return hasItemOutput;
    }

    @Override
    public void setStats() {
        stats.timePeriod = craftTime;

        stats.add(Stat.size, "@x@", size, size);

        if (synthetic()) {
            stats.add(Stat.health, health, StatUnit.none);
            if (armor > 0) {
                stats.add(Stat.armor, armor, StatUnit.none);
            }
        }

        if (canBeBuilt() && requirements.length > 0) {
            stats.add(Stat.buildTime, buildTime / 60, StatUnit.seconds);
            stats.add(Stat.buildCost, StatValues.items(false, requirements));
        }

        for (var c : consumers) {
            c.display(stats);
        }

        if (hasLiquids) stats.add(Stat.liquidCapacity, liquidCapacity, StatUnit.liquidUnits);
        if (hasItems && itemCapacity > 0) stats.add(Stat.itemCapacity, itemCapacity, StatUnit.items);

        stats.add(Stat.output, table -> {
            table.clearChildren();
            table.left();

            for (int i = 0; i < recipes.size; i++) {
                Recipe rec = recipes.get(i);
                rec.ensureArrays();
                int idx = i;

                table.table(Styles.grayPanel, t -> {
                    t.left().defaults().left().padLeft(4);

                    // 标题
                    t.add("[accent]Recipe " + (idx + 1) + "[]").padTop(4).padBottom(4);
                    t.row();

                    // 输入行
                    boolean hasInput = rec.inputItems.length > 0 || rec.inputLiquids.length > 0
                            || rec.inputPower > 0 || rec.inputHeat > 0 || rec.cachedInputPayloads.length > 0;
                    if (hasInput) {
                        t.add("[lightgray]" + Core.bundle.get("stat.input") + ":[]").padRight(8);

                        for (ItemStack s : rec.inputItems) {
                            t.add(StatValues.displayItem(s.item, s.amount, rec.craftTime, true)).padRight(8);
                        }
                        for (LiquidStack s : rec.inputLiquids) {
                            t.add(StatValues.displayLiquid(s.liquid, s.amount * 60f, true)).padRight(8);
                        }
                        for (PayloadStack s : rec.cachedInputPayloads) {
                            t.table(pl -> {
                                pl.image(s.item.uiIcon).size(32).padRight(2);
                                pl.add(s.item.localizedName).color(Color.lightGray).padRight(4);
                                pl.add(Strings.autoFixed(s.amount / (rec.craftTime / 60f), 1) + StatUnit.perSecond.localized()).color(Color.lightGray);
                            }).padRight(8);
                        }
                        if (rec.inputPower > 0) {
                            t.table(p -> StatValues.number(rec.inputPower * 60f, StatUnit.powerSecond).display(p)).padRight(8);
                        }
                        if (rec.inputHeat > 0) {
                            t.table(h -> StatValues.number(rec.inputHeat, StatUnit.heatUnits).display(h)).padRight(8);
                        }
                        t.row();
                    }

                    // 输出行
                    boolean hasOutput = rec.outputItems.length > 0 || rec.outputLiquids.length > 0
                            || rec.outputHeat > 0 || rec.outputPower > 0 || rec.cachedOutputPayloads.length > 0;
                    if (hasOutput) {
                        t.add("[lightgray]" + Core.bundle.get("stat.output") + ":[]").padRight(8);

                        for (ItemStack s : rec.outputItems) {
                            t.add(StatValues.displayItem(s.item, s.amount, rec.craftTime, true)).padRight(8);
                        }
                        for (LiquidStack s : rec.outputLiquids) {
                            t.add(StatValues.displayLiquid(s.liquid, s.amount * 60f, true)).padRight(8);
                        }
                        for (PayloadStack s : rec.cachedOutputPayloads) {
                            t.table(pl -> {
                                pl.image(s.item.uiIcon).size(32).padRight(2);
                                pl.add(s.item.localizedName).color(Color.lightGray).padRight(4);
                                pl.add(Strings.autoFixed(s.amount / (rec.craftTime / 60f), 1) + StatUnit.perSecond.localized()).color(Color.lightGray);
                            }).padRight(8);
                        }
                        if (rec.outputPower > 0) {
                            t.table(p -> StatValues.number(rec.outputPower * 60f, StatUnit.powerSecond).display(p)).padRight(8);
                        }
                        if (rec.outputHeat > 0) {
                            t.table(h -> StatValues.number(rec.outputHeat, StatUnit.heatUnits).display(h)).padRight(8);
                        }
                        t.row();
                    }

                    // 底部：生产时间 最大效率
                    t.add("[lightgray]" + Core.bundle.get("stat.productiontime") + ":[] " + Strings.autoFixed(rec.craftTime / 60f, 3) + " " + Core.bundle.get("unit.seconds")).padTop(4);


                    if (rec.inputHeat > 0) {
                        t.add("  [lightgray]最大效率:[] " + Strings.autoFixed(rec.maxEfficiency * 100f, 0) + "%");
                    }

                }).growX().pad(5).row();
            }
        });
    }

    @Override
    public void setBars() {
        super.setBars();

        removeBar("items");
        removeBar("heat");
        removeBar("liquid");

        if (showWarmup) {
            addBar("warmup", (MultiCrafterBuild entity) ->
                    new Bar(() -> Core.bundle.get("bar.warmup"), () -> Pal.accent, () -> entity.warmup));
        }

        ObjectSet<Liquid> allLiquids = new ObjectSet<>();
        for (Recipe rec : recipes) {
            for (LiquidStack stack : rec.inputLiquids) allLiquids.add(stack.liquid);
            for (LiquidStack stack : rec.outputLiquids) allLiquids.add(stack.liquid);
        }
        if (allLiquids.size > 0) {
            for (Liquid liquid : allLiquids) {
                addBar("liquid-" + liquid.name, (MultiCrafterBuild entity) ->
                        new Bar(() -> liquid.localizedName, liquid::barColor,
                                () -> entity.liquids.get(liquid) / liquidCapacity));
            }
        }

        if (recipes.contains(r -> r.outputPower > 0)) {
            addBar("power-output", (MultiCrafterBuild entity) ->
                    new Bar(() -> Core.bundle.format("bar.poweroutput", entity.getPowerProduction() * 60 * entity.timeScale()),
                            () -> Pal.powerBar, () -> entity.getPowerStat()));
        }

        if (recipes.contains(r -> r.inputHeat > 0)) {
            addBar("heat-input", (MultiCrafterBuild entity) -> {
                Recipe rec = entity.getCurrentRecipe();
                float need = rec != null && rec.inputHeat > 0 ? rec.inputHeat :
                        recipes.find(r -> r.inputHeat > 0) != null ? recipes.find(r -> r.inputHeat > 0).inputHeat : 1f;
                return new Bar(
                        () -> "热量输入：" + (int)(entity.heatInput + 0.01f) + " (" + (int)(entity.heatEfficiencyScale() * 100 + 0.01f) + "%)",
                        () -> Pal.lightOrange,
                        () -> need > 0 ? Mathf.clamp(entity.heatInput / need) : 0f
                );
            });
        }

        if (recipes.contains(r -> r.outputHeat > 0)) {
            addBar("heat-output", (MultiCrafterBuild entity) -> {
                Recipe rec = entity.getCurrentRecipe();
                float max = rec != null && rec.outputHeat > 0 ? rec.outputHeat :
                        recipes.find(r -> r.outputHeat > 0) != null ? recipes.find(r -> r.outputHeat > 0).outputHeat : 1f;
                return new Bar(
                        () -> "热量输出：" + (int)(entity.heatOutput + 0.01f) + " (" + (int)(entity.heatOutput / max * 100 + 0.01f) + "%)",
                        () -> Pal.lightOrange,
                        () -> max > 0 ? Mathf.clamp(entity.heatOutput / max) : 0f
                );
            });
        }
    }



    @Override
    public boolean configSenseable() {
        return configurations.containsKey(Integer.class) || super.configSenseable();
    }

    public class MultiCrafterBuild extends HeatCrafterBuild implements HeatBlock {
        public ObjectSet<Item> outputItemsSet = new ObjectSet<>();
        public ObjectSet<Liquid> outputLiquidsSet = new ObjectSet<>();
        public int selectRecipe = 0;
        public float heatInput = 0f;
        public float heatOutput = 0f;
        public float warmupRate = 0.15f;

        public @Nullable Payload payload;
        public Vec2 payVector = new Vec2();
        public float payRotation;
        public boolean carried;
        public IntMap<Integer> payloadCounts = new IntMap<>();

        public @Nullable Recipe currentVisualRecipe;
        public ObjectSet<UnlockableContent> outputPayloadsSet = new ObjectSet<>();




        public float heatEfficiencyScale() {
            Recipe rec = getCurrentRecipe();


            if (rec == null && selectRecipe > 0) {
                int idx = selectRecipe - 1;
                if (idx >= 0 && idx < recipes.size) {
                    rec = recipes.get(idx);
                }
            }

            if (rec == null || rec.inputHeat <= 0) {
                return heatInput > 0.001f ? 1f : 0f;
            }

            float req = rec.inputHeat;
            float over = Math.max(heatInput - req, 0f);
            return Math.min(
                    Mathf.clamp(heatInput / req) + over / req * overheatScale,
                    rec.maxEfficiency
            );
        }


        @Override
        @Nullable
        public PayloadSeq getPayloads() {
            if (payloadCounts.size == 0 && payload == null) return null;

            PayloadSeq seq = new PayloadSeq();

            // 动画中的载荷
            if (payload != null) {
                seq.add(payload.content(), 1);
            }

            // 库存
            for (IntMap.Entry<Integer> entry : payloadCounts.entries()) {
                UnlockableContent content = Vars.content.block(entry.key);
                if (content == null) content = Vars.content.unit(entry.key);
                if (content != null) {
                    seq.add(content, entry.value);
                }
            }

            return seq;
        }




        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();


            TextButton autoBtn = new TextButton("自动", Styles.flatTogglet);
            autoBtn.setChecked(selectRecipe == 0);
            autoBtn.update(() -> autoBtn.setChecked(selectRecipe == 0));
            autoBtn.changed(() -> {
                if (selectRecipe != 0) configure(0);
            });
            table.add(autoBtn).height(40f).growX().pad(4f).row();

            // 分隔线
            table.image().color(Pal.gray).height(2f).growX().padBottom(4f).row();


            Table recipeList = new Table();
            recipeList.left();

            for (int i = 0; i < recipes.size; i++) {
                final int recipeNum = i + 1;
                Recipe rec = recipes.get(i);


                TextButton recipeBtn = new TextButton("", Styles.flatTogglet);
                recipeBtn.setChecked(selectRecipe == recipeNum);


                Table content = new Table();
                content.left();

                // 配方编号
                content.add("[accent]" + recipeNum + "[]").padLeft(6f).padRight(8f).width(24f);

                // 输入图标
                Table inputTable = new Table();
                inputTable.left();
                if (rec.inputItems.length > 0 || rec.inputLiquids.length > 0
                        || rec.cachedInputPayloads.length > 0 || rec.inputPower > 0 || rec.inputHeat > 0) {
                    for (ItemStack stack : rec.inputItems) {
                        inputTable.image(stack.item.uiIcon).size(24f).padRight(2f);
                    }
                    for (LiquidStack stack : rec.inputLiquids) {
                        inputTable.image(stack.liquid.uiIcon).size(24f).padRight(2f);
                    }
                    for (PayloadStack stack : rec.cachedInputPayloads) {
                        inputTable.image(stack.item.uiIcon).size(24f).padRight(2f);
                    }
                    if (rec.inputPower > 0) {
                        inputTable.add("[accent]" + Iconc.power + "[]").padRight(2f);
                    }
                    if (rec.inputHeat > 0) {
                        inputTable.add("[red]" + Iconc.waves + "[]").padRight(2f);
                    }
                } else {
                    inputTable.add("[darkGray]-[]");
                }
                content.add(inputTable).padRight(8f);

                // 箭头
                content.add("[accent]"+ Iconc.right +"[]").padRight(8f).padLeft(4f);

                // 输出图标
                Table outputTable = new Table();
                outputTable.left();
                if (rec.outputItems.length > 0 || rec.outputLiquids.length > 0
                        || rec.cachedOutputPayloads.length > 0 || rec.outputPower > 0 || rec.outputHeat > 0) {
                    for (ItemStack stack : rec.outputItems) {
                        outputTable.image(stack.item.uiIcon).size(24f).padRight(2f);
                    }
                    for (LiquidStack stack : rec.outputLiquids) {
                        outputTable.image(stack.liquid.uiIcon).size(24f).padRight(2f);
                    }
                    for (PayloadStack stack : rec.cachedOutputPayloads) {
                        outputTable.image(stack.item.uiIcon).size(24f).padRight(2f);
                    }
                    if (rec.outputPower > 0) {
                        outputTable.add("[accent]" + Iconc.power + "[]").padRight(2f);
                    }
                    if (rec.outputHeat > 0) {
                        outputTable.add("[red]" + Iconc.waves + "[]").padRight(2f);
                    }
                }
                content.add(outputTable);

                // 将内容添加到按钮
                recipeBtn.add(content).growX().pad(2f);

                // 点击事件
                recipeBtn.changed(() -> {
                    if (selectRecipe == recipeNum) {
                        configure(0);
                    } else {
                        configure(recipeNum);
                    }
                });

                // 动态更新选中状态
                recipeBtn.update(() -> {
                    recipeBtn.setChecked(selectRecipe == recipeNum);
                });

                recipeList.add(recipeBtn).growX().height(44f).padBottom(3f).row();
            }

            // 包装成滚动面板
            ScrollPane pane = new ScrollPane(recipeList, Styles.smallPane);
            pane.setScrollingDisabled(true, false);
            pane.setOverscroll(false, false);
            pane.setFadeScrollBars(false);
            pane.setClamp(true);
            pane.setCancelTouchFocus(true);
            pane.setFlickScroll(true);

            // 最多显示3个配方行
            float rowHeight = 48f;
            float maxHeight = Math.min(recipes.size, 3) * rowHeight;
            table.add(pane).growX().maxHeight(maxHeight).pad(4f);
        }


        @Override
        public Object config() {
            return selectRecipe;
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof Integer val && val >= 0 && val <= recipes.size) {
                if (selectRecipe != val) {

                    selectRecipe = val;
                    progress = 0f;
                    outputLiquidsSet.clear();

                    if (selectRecipe != 0) {
                        currentVisualRecipe = recipes.get(selectRecipe - 1);
                    } else {
                        currentVisualRecipe = null;
                    }

                    // 播放音效
                    Recipe newRec = getCurrentRecipe();
                    Effect switchEff = (newRec != null && newRec.switchEffect != null) ? newRec.switchEffect : Fx.rotateBlock;
                    switchEff.at(x, y, block.size);
                }
            }
        }

        @Override
        public double sense(LAccess sensor) {
            if (sensor == LAccess.config) return selectRecipe;
            return super.sense(sensor);
        }

        @Override
        public Object senseObject(LAccess sensor) {
            if (sensor == LAccess.config) return Senseable.noSensed;
            return super.senseObject(sensor);
        }

        @Override
        public void control(LAccess type, double p1, double p2, double p3, double p4) {
            if (type == LAccess.config) {
                int val = (int) Math.round(p1);
                if (val >= 0 && val <= recipes.size) {
                    if (selectRecipe != val) {
                        selectRecipe = val;
                        progress = 0f;
                        outputLiquidsSet.clear();
                    }
                }
                return;
            }
            super.control(type, p1, p2, p3, p4);
        }

        @Nullable
        public Recipe getCurrentRecipe() {
            if (selectRecipe == 0) {
                for (Recipe rec : recipes) {
                    if (rec.inputHeat > 0 && heatInput <= 0f) continue;
                    if (rec.inputPower > 0 && (power == null || power.status <= 0f)) continue;
                    if (checkInput(rec) && checkOutput(rec)) return rec;
                }
                return null;
            } else {
                int idx = selectRecipe - 1;
                if (idx >= 0 && idx < recipes.size) {
                    Recipe rec = recipes.get(idx);
                    if (checkInput(rec) && checkOutput(rec)) return rec;
                }
                return null;
            }
        }

        @Override
        public BlockStatus status() {
            if (!enabled) return BlockStatus.logicDisable;
            if (!shouldConsume()) return BlockStatus.noOutput;
            Recipe rec = getCurrentRecipe();
            if (rec == null) return BlockStatus.noInput;
            if (efficiencyScale() > 0 && productionValid()) {
                return Vars.state.tick / 30.0 % 1.0 < efficiencyScale() ? BlockStatus.active : BlockStatus.noInput;
            }
            return BlockStatus.noInput;
        }


        private boolean checkInput(Recipe rec) {
            if (rec.inputPower > 0 && (power == null || power.status <= 0f)) return false;
            if (rec.inputHeat > 0 && heatInput <= 0f) return false;
            for (ItemStack stack : rec.inputItems) {
                if (items.get(stack.item) < stack.amount) return false;
            }
            for (PayloadStack stack : rec.cachedInputPayloads) {
                if (getPayloadCount(stack.item) < stack.amount) return false;
            }
            for (LiquidStack stack : rec.inputLiquids) {
                if (liquids.get(stack.liquid) <= 0.001f) return false;
            }
            return true;
        }

        private boolean checkOutput(Recipe rec) {
            for (ItemStack stack : rec.outputItems) {
                if (items.get(stack.item) + stack.amount > itemCapacity) return false;
            }

            if (rec.outputLiquids.length > 0) {
                boolean allFull = true;
                for (LiquidStack output : rec.outputLiquids) {
                    float inputAmount = 0f;
                    for (LiquidStack input : rec.inputLiquids) {
                        if (input.liquid == output.liquid) {

                            inputAmount = input.amount;
                            break;
                        }
                    }

                    float netOutput = output.amount - inputAmount;
                    if (netOutput > 0) {
                        if (liquids.get(output.liquid) < liquidCapacity - 0.001f) {
                            allFull = false;
                            break;
                        }
                    } else {
                        allFull = false;
                    }
                }
                if (allFull) return false;
            }

            for (PayloadStack stack : rec.cachedOutputPayloads) {
                if (getPayloadCount(stack.item) + stack.amount > payloadCapacity) return false;
            }
            return true;
        }

        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            for (Building other : proximity) {
                if (other instanceof Conduit.ConduitBuild) {
                    other.onProximityUpdate();
                }
            }
        }

        public int getPayloadCount(UnlockableContent content) {
            return payloadCounts.get(content.id, 0);
        }

        public void addPayload(UnlockableContent content, int amount) {
            payloadCounts.put(content.id, getPayloadCount(content) + amount);
        }

        public void removePayload(UnlockableContent content, int amount) {
            int current = getPayloadCount(content);
            if (current <= amount) {
                payloadCounts.remove(content.id);
            } else {
                payloadCounts.put(content.id, current - amount);
            }
        }



        @Override
        public float calculateHeat(float[] sideHeat) {
            return calculateHeat(sideHeat, null);
        }

        @Override
        public float calculateHeat(float[] sideHeat, IntSet cameFrom) {
            java.util.Arrays.fill(sideHeat, 0f);
            if (cameFrom != null) cameFrom.clear();

            float heat = 0f;
            IntSet visited = new IntSet();
            visited.add(this.id());

            for (Building build : this.proximity) {
                if (build == null || build.team != team || !(build instanceof HeatBlock heater)) continue;
                if (build == this) continue;
                if (visited.contains(build.id)) continue;
                visited.add(build.id);

                boolean split = false;
                if (build.block instanceof HeatConductor cond && cond.splitHeat) {
                    split = true;
                }

                if (!build.block.rotate || !split && (this.relativeTo(build) + 2) % 4 == build.rotation
                        || split && this.relativeTo(build) != build.rotation) {

                    float diff = Math.min(Math.abs(build.x - this.x), Math.abs(build.y - this.y)) / 8f;
                    int contactPoints = Math.min(
                            (int)(this.block.size / 2f + build.block.size / 2f - diff),
                            Math.min(build.block.size, this.block.size)
                    );

                    float add = heater.heat() / build.block.size * contactPoints;
                    if (split) add /= 3f;

                    int dir = Mathf.mod(this.relativeTo(build), 4);
                    if (dir >= 0) {
                        sideHeat[dir] += add;
                        heat += add;
                    }

                    if (heater instanceof HeatConductor.HeatConductorBuild hc) {
                        hc.updateHeat();
                    }
                }
            }

            return heat;
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            if (!hasPayloads) return false;
            if (this.payload != null) return false;

            for (Recipe rec : recipes) {
                for (PayloadStack stack : rec.cachedInputPayloads) {
                    if (stack.item == payload.content()) {
                        return getPayloadCount(stack.item) < payloadCapacity;
                    }
                }
            }
            return false;
        }

        @Override
        public void handlePayload(Building source, Payload payload) {
            this.payload = payload;
            this.payVector.set(source).sub(this).clamp(
                    -size * tilesize / 2f, -size * tilesize / 2f,
                    size * tilesize / 2f, size * tilesize / 2f
            );
            this.payRotation = payload.rotation();
            updatePayload();
        }

        @Override
        public Payload getPayload() {
            return payload;
        }

        @Override
        public Payload takePayload() {
            Payload t = payload;
            payload = null;
            return t;
        }

        public boolean moveInPayload() {
            return moveInPayload(true);
        }

        public boolean moveInPayload(boolean rotate) {
            if (payload == null) return false;
            updatePayload();

            if (rotate) {
                payRotation = Mathf.approachDelta(payRotation, block.rotate ? rotdeg() : 90f, payloadRotateSpeed * delta());
            }
            payVector.approach(Vec2.ZERO, payloadSpeed * delta());

            if (payVector.isZero(0.01f)) {
                addPayload(payload.content(), 1);
                payload = null;
                return true;
            }
            return false;
        }

        public void updatePayload() {
            if (payload != null) {
                payload.set(x + payVector.x, y + payVector.y, payRotation);
            }
        }

        @Override
        public void pickedUp() {
            carried = true;
        }

        @Override
        public void drawTeamTop() {
            carried = false;
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            if (payload != null && !carried) payload.dump();
        }

        @Override
        public void onDestroyed() {
            if (payload != null) payload.destroyed();
            super.onDestroyed();
        }

        @Override
        public double sense(Content content) {
            if (payload instanceof UnitPayload up && up.unit.type == content) return 1;
            if (payload instanceof BuildPayload bp && bp.block() == content) return 1;

            int count = payloadCounts.get(content.id, 0);
            if (count > 0) return count;

            return super.sense(content);
        }





        @Override
        public void updateTile() {
            // 处理输入载荷动画
            if (payload != null) {
                payload.update(null, this);
                moveInPayload();
            }

            heat = calculateHeat(sideHeat);
            heatInput = heat;

            Recipe active = getCurrentRecipe();



            // 视觉配方更新
            if (selectRecipe == 0) {
                if (active != null && active != currentVisualRecipe) {
                    currentVisualRecipe = active;
                    outputLiquidsSet.clear();
                } else if (active == null && currentVisualRecipe == null) {
                    for (Recipe r : recipes) {
                        if (r.visual != null) {
                            currentVisualRecipe = r;
                            break;
                        }
                    }
                }
            }


            float totalRatio = active != null ? efficiencyScale() : 0f;
            boolean canProduce = totalRatio > 0.001f;

            // 热量输出
            float targetHeat = 0f;
            if (canProduce && active.outputHeat > 0) {
                targetHeat = active.outputHeat * efficiencyScale();
            }
            heatOutput = Mathf.approachDelta(heatOutput, targetHeat, warmupRate * delta());

            if (canProduce) {
                // 消耗液体
                for (LiquidStack stack : active.inputLiquids) {
                    float consume = stack.amount * delta() * totalRatio;
                    if (consume > 0) liquids.remove(stack.liquid, consume);
                }

                // 进度
                float progressInc = delta() / active.craftTime * totalRatio;
                progress += progressInc;
                warmup = Mathf.lerpDelta(warmup, warmupTarget(), warmupRate);

                // 输出液体
                if (active.outputLiquids.length > 0) {
                    float inc = delta() * totalRatio;
                    for (LiquidStack output : active.outputLiquids) {
                        float amount = output.amount * inc;
                        if (amount > 0) {
                            float canAdd = Math.min(amount, liquidCapacity - liquids.get(output.liquid));
                            if (canAdd > 0) {
                                handleLiquid(this, output.liquid, canAdd);
                                outputLiquidsSet.add(output.liquid);
                            }
                        }
                    }
                }

                // 音效特效
                if (active.updateSound != null && wasVisible && Mathf.chanceDelta(active.updateEffectChance > 0 ? active.updateEffectChance : updateEffectChance)) {
                    active.updateSound.at(x, y);
                }
                Effect eff = active.updateEffect != null ? active.updateEffect : updateEffect;
                if (wasVisible && eff != Fx.none && Mathf.chanceDelta(updateEffectChance)) {
                    eff.at(x + Mathf.range(size * 4), y + Mathf.range(size * 4), 0f, Color.white);
                }

                if (progress >= 1f) {
                    craft(active);
                }
            } else {
                warmup = Mathf.lerpDelta(warmup, 0f, warmupRate);
            }

            dumpOutputs();
            totalProgress += warmup * Time.delta;
        }

        public void craft(Recipe rec) {
            // 消耗物品和载荷
            for (ItemStack stack : rec.inputItems) items.remove(stack.item, stack.amount);
            for (PayloadStack stack : rec.cachedInputPayloads) removePayload(stack.item, stack.amount);

            // 产出物品、载荷
            for (ItemStack stack : rec.outputItems) {
                items.add(stack.item, stack.amount);
                outputItemsSet.add(stack.item);
            }
            for (PayloadStack stack : rec.cachedOutputPayloads) {
                addPayload(stack.item, stack.amount);
                outputPayloadsSet.add(stack.item);
            }

            progress = 0f;
            if (wasVisible) {
                //特效
                Effect effect = rec.craftEffect != null ? rec.craftEffect : craftEffect;
                effect.at(x, y);

                //音效
                if (rec.craftSound != null) {
                    rec.craftSound.at(x, y);
                }
            }
        }

        public void dumpOutputs() {
            // 物品
            for (Item item : outputItemsSet) {
                while (items.has(item) && dump(item)) {}
                if (!items.has(item)) outputItemsSet.remove(item);
            }

            Seq<Liquid> liquidsToDump = new Seq<>();
            Recipe current = null;

            if (selectRecipe == 0) {
                // 自动模式
                liquidsToDump.addAll(outputLiquidsSet);
            } else {
                // 手动模式
                int idx = selectRecipe - 1;
                if (idx >= 0 && idx < recipes.size) {
                    current = recipes.get(idx);
                    for (LiquidStack stack : current.outputLiquids) {
                        liquidsToDump.add(stack.liquid);
                    }
                }
            }

            // 液体
            for (Liquid liquid : liquidsToDump) {
                if (liquids.get(liquid) > 0.001f) {
                    dumpLiquid(liquid);
                }
            }

            if (selectRecipe == 0) {
                Seq<Liquid> toRemove = new Seq<>();
                for (Liquid liquid : outputLiquidsSet) {
                    if (liquids.get(liquid) <= 0.001f) toRemove.add(liquid);
                }
                for (Liquid liquid : toRemove) outputLiquidsSet.remove(liquid);
            }

            // 载荷
            Seq<UnlockableContent> payloadsToDump = new Seq<>();
            if (selectRecipe == 0) {
                payloadsToDump.addAll(outputPayloadsSet);
            } else if (current != null) {
                for (PayloadStack stack : current.cachedOutputPayloads) {
                    payloadsToDump.add(stack.item);
                }
            }

            for (UnlockableContent content : payloadsToDump) {
                while (getPayloadCount(content) > 0) {
                    Payload output = createPayload(content);
                    if (output != null && dumpPayload(output)) {
                        removePayload(content, 1);
                    } else {
                        break;
                    }
                }
            }
            if (selectRecipe == 0) {
                Seq<UnlockableContent> toRemove = new Seq<>();
                for (UnlockableContent content : outputPayloadsSet) {
                    if (getPayloadCount(content) == 0) toRemove.add(content);
                }
                for (UnlockableContent content : toRemove) outputPayloadsSet.remove(content);
            }
        }


        private Payload createPayload(UnlockableContent content) {
            if (content instanceof Block b) {
                return new BuildPayload(b, team);
            } else if (content instanceof UnitType ut) {
                return new UnitPayload(ut.create(team));
            }
            return null;

        }

        @Override
        public boolean shouldConsume() {
            Recipe rec;
            if (selectRecipe == 0) {
                // 自动模式
                for (Recipe r : recipes) {
                    if (checkOutput(r)) return enabled;
                }
                return false;
            } else {
                // 手动模式
                int idx = selectRecipe - 1;
                if (idx < 0 || idx >= recipes.size) return false;
                rec = recipes.get(idx);
            }

            if (!checkOutput(rec)) return false;
            return enabled;
        }



        @Override
        public float warmupTarget() {
            Recipe rec = getCurrentRecipe();
            if (rec != null && rec.inputHeat > 0) {
                if (heatRequirement() <= 0f) return 1f;
                return Mathf.clamp(heatInput / heatRequirement());
            }
            return 1f;
        }

        @Override
        public float efficiencyScale() {
            Recipe rec = getCurrentRecipe();
            if (rec == null) return 0f;

            // 热量效率
            float scale = 1f;
            if (rec.inputHeat > 0) {
                float req = rec.inputHeat;
                float over = Math.max(heatInput - req, 0f);
                scale = Math.min(
                        Mathf.clamp(heatInput / req) + over / req * overheatScale,
                        rec.maxEfficiency
                );
            }

            // 电力效率
            if (rec.inputPower > 0) {
                scale *= power == null ? 0f : power.status;
            }

            // 液体效率
            for (LiquidStack stack : rec.inputLiquids) {
                float have = liquids.get(stack.liquid);

                float need = stack.amount * delta() * scale;
                if (need > 0) {
                    scale *= Math.min(have / need, 1f);
                }
            }

            // 物品检查
            for (ItemStack stack : rec.inputItems) {
                if (items.get(stack.item) < stack.amount) return 0f;
            }

            // 载荷检查
            for (PayloadStack stack : rec.cachedInputPayloads) {
                if (getPayloadCount(stack.item) < stack.amount) return 0f;
            }

            return scale;
        }

        @Override
        public float heatRequirement() {
            Recipe rec = getCurrentRecipe();
            return rec == null ? 0f : rec.inputHeat;
        }

        @Override
        public float heat() {
            Recipe rec = getCurrentRecipe();
            if (rec != null && rec.outputHeat > 0 && heatOutput > 0.001f) {
                return heatOutput;
            }
            return 0f;
        }

        @Override
        public float heatFrac() {
            Recipe rec = getCurrentRecipe();
            if (rec != null && rec.outputHeat > 0) {
                return heatOutput / rec.outputHeat;
            }
            return 0f;
        }

        public float getPowerProduction() {
            Recipe rec = getCurrentRecipe();
            return (rec != null && rec.outputPower > 0) ? rec.outputPower * efficiencyScale() : 0f;
        }

        public float getPowerStat() {
            Recipe rec = getCurrentRecipe();
            return (rec != null && rec.outputPower > 0) ? efficiencyScale() : 0f;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(selectRecipe);
            write.f(heatInput);
            write.f(heatOutput);
            write.s(outputItemsSet.size);
            for (Item item : outputItemsSet) write.s(item.id);
            write.s(outputLiquidsSet.size);
            for (Liquid liquid : outputLiquidsSet) write.s(liquid.id);

            write.s(outputPayloadsSet.size);
            for (UnlockableContent content : outputPayloadsSet) write.s(content.id);

            write.f(payVector.x);
            write.f(payVector.y);
            write.f(payRotation);
            Payload.write(payload, write);

            write.s(payloadCounts.size);
            for (IntMap.Entry<Integer> entry : payloadCounts.entries()) {
                write.s(entry.key);
                write.i(entry.value);
            }

            int idx = currentVisualRecipe == null ? -1 : recipes.indexOf(currentVisualRecipe);
            write.i(idx);

        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            selectRecipe = read.i();
            selectRecipe = Mathf.clamp(selectRecipe, 0, recipes.size);
            heatInput = read.f();
            heatOutput = read.f();
            outputItemsSet.clear();
            int itemSize = read.s();
            for (int i = 0; i < itemSize; i++) outputItemsSet.add(content.item(read.s()));
            outputLiquidsSet.clear();
            int liquidSize = read.s();
            for (int i = 0; i < liquidSize; i++) outputLiquidsSet.add(content.liquid(read.s()));

            outputPayloadsSet.clear();
            int payloadSetSize = read.s();
            for (int i = 0; i < payloadSetSize; i++) {
                int id = read.s();
                UnlockableContent c = content.block(id);
                if (c == null) c = content.unit(id);
                if (c != null) outputPayloadsSet.add(c);
            }

            payVector.set(read.f(), read.f());
            payRotation = read.f();
            payload = Payload.read(read);

            payloadCounts.clear();
            int payloadSize = read.s();
            for (int i = 0; i < payloadSize; i++) {
                int id = read.s();
                int count = read.i();
                payloadCounts.put(id, count);
            }

            int idx = read.i();
            if (idx >= 0 && idx < recipes.size) {
                currentVisualRecipe = recipes.get(idx);
            } else {
                currentVisualRecipe = null;
            }

        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.get(item) >= itemCapacity) return false;
            return recipes.contains(r -> {
                for (ItemStack stack : r.inputItems) if (stack.item == item) return true;
                return false;
            });
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            return recipes.contains(r -> {
                for (LiquidStack stack : r.inputLiquids) if (stack.liquid == liquid) return true;
                return false;
            });
        }

        @Override
        public void handleItem(Building source, Item item) {
            items.add(item, 1);
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source) {
            return Math.min(amount, itemCapacity - items.get(item));
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source) {
            items.add(item, amount);
        }

        @Override
        public void draw() {
            drawer.draw(this);
            if (currentVisualRecipe != null && currentVisualRecipe.visual != null) {
                currentVisualRecipe.visual.draw(this);
            }
            drawPayload();
            drawStatus();
        }

        public void drawPayload() {
            // 绘制输入载荷
            if (payload != null) {
                updatePayload();
                Draw.z(Layer.blockOver);
                payload.draw();
            }
        }

        @Override
        public void drawStatus() {
            if (block.enableDrawStatus) {
                float multiplier = block.size > 1 ? 1.0F : 0.64F;
                float brcx = x + (float) (block.size * 8) / 2.0F - 8.0F * multiplier / 2.0F;
                float brcy = y - (float) (block.size * 8) / 2.0F + 8.0F * multiplier / 2.0F;
                Draw.z(71.0F);
                Draw.color(Pal.gray);
                Fill.square(brcx, brcy, 2.5F * multiplier, 45.0F);
                Draw.color(status().color);
                Fill.square(brcx, brcy, 1.5F * multiplier, 45.0F);
                Draw.color();
            }
        }
    }



    public static class PayloadStack {
        public UnlockableContent item;
        public int amount;
        public PayloadStack(UnlockableContent item, int amount) {
            this.item = item;
            this.amount = amount;
        }
    }

    public static class Recipe {
        public ItemStack[] inputItems = {};
        public LiquidStack[] inputLiquids = {};
        public float inputPower = 0f;
        public float inputHeat = 0f;
        public ItemStack[] outputItems = {};
        public LiquidStack[] outputLiquids = {};
        public float outputPower = 0f;
        public float outputHeat = 0f;
        public float craftTime = 60f;

        public String[] inputPayloads = {};
        public String[] outputPayloads = {};

        public @Nullable DrawBlock visual = null;


        public float maxEfficiency = 4f;
        public @Nullable Effect craftEffect;
        public @Nullable Effect updateEffect;
        public float updateEffectChance = 0.04f;
        public @Nullable Effect switchEffect;
        public @Nullable Sound craftSound;
        public @Nullable Sound updateSound;

        public transient PayloadStack[] cachedInputPayloads = {};
        public transient PayloadStack[] cachedOutputPayloads = {};


        public Recipe() {
            ensureArrays();
        }

        public void ensureArrays() {
            if (inputItems == null) inputItems = new ItemStack[0];
            if (inputLiquids == null) inputLiquids = new LiquidStack[0];
            if (outputItems == null) outputItems = new ItemStack[0];
            if (outputLiquids == null) outputLiquids = new LiquidStack[0];
            if (inputPayloads == null) inputPayloads = new String[0];
            if (outputPayloads == null) outputPayloads = new String[0];
            if (cachedInputPayloads == null) cachedInputPayloads = new PayloadStack[0];
            if (cachedOutputPayloads == null) cachedOutputPayloads = new PayloadStack[0];
        }




        public Recipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                      ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                      float craftTime, PayloadStack[] inputPayloads, PayloadStack[] outputPayloads, @Nullable DrawBlock visual) {

            this.inputItems = inputItems;
            this.inputLiquids = inputLiquids;
            this.inputPower = inputPower;
            this.inputHeat = inputHeat;
            this.outputItems = outputItems;
            this.outputLiquids = outputLiquids;
            this.outputPower = outputPower;
            this.outputHeat = outputHeat;
            this.craftTime = craftTime;
            this.cachedInputPayloads = inputPayloads;
            this.cachedOutputPayloads = outputPayloads;
            this.visual = visual;

            this.inputPayloads = new String[0];
            this.outputPayloads = new String[0];
            ensureArrays();
        }

        public Recipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                      ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                      float craftTime) {
            this(inputItems, inputLiquids, inputPower, inputHeat,
                    outputItems, outputLiquids, outputPower, outputHeat,
                    craftTime, new PayloadStack[0], new PayloadStack[0], null);
        }

        public Recipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                      ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                      float craftTime, @Nullable DrawBlock visual) {
            this(inputItems, inputLiquids, inputPower, inputHeat,
                    outputItems, outputLiquids, outputPower, outputHeat,
                    craftTime, new PayloadStack[0], new PayloadStack[0], visual);
        }

        public Recipe(ItemStack[] inputItems, LiquidStack[] inputLiquids, float inputPower, float inputHeat,
                      ItemStack[] outputItems, LiquidStack[] outputLiquids, float outputPower, float outputHeat,
                      float craftTime, PayloadStack[] inputPayloads, PayloadStack[] outputPayloads) {
            this(inputItems, inputLiquids, inputPower, inputHeat,
                    outputItems, outputLiquids, outputPower, outputHeat,
                    craftTime, inputPayloads, outputPayloads, null);
        }
    }
}