package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.obstacle.generator.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;
import java.nio.file.*;
import java.util.zip.*;

public final class ServerSmokeChecks extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("NDG_SMOKE_PASS"); }
            catch(Throwable failure) { getLogger().log(java.util.logging.Level.SEVERE,"NDG_SMOKE_FAIL",failure); }
            finally { Bukkit.shutdown(); }
        }, 40);
    }
    private void verify() throws Exception {
        var manager=NDGManager.getInstance();
        String name="ndg_smoke_"+UUID.randomUUID();
        var grid=new CourseGrid(23,9,List.of(TileTypes.values()),3,2026);
        var course=new CourseImpl(Division.JUNIOR,grid,name);
        var settings=new DefaultRoundSettings(8,RoundType.RECREATIONAL,Division.JUNIOR,false);
        var round=(RoundImpl)manager.getRoundHandler().createRound("ffa",course,name,settings);
        World world=Bukkit.getWorld(name);
        check(world!=null,"world created");
        new ObstacleGenerator(grid,Division.JUNIOR).generateObstacles(world);
        Location tee=course.tee(world,0);
        check(world.getBlockAt(tee.clone().subtract(0,1,0)).getType()==Material.SMOOTH_STONE,"tee anchor matches rendered tee");
        Location basket=course.basket(world,0);
        var goal=MathUtil.trace(round,basket.clone().add(-3,0,0),new Vector(6,0,0));
        check(goal!=null && goal.goal(),"current basket scores along swept segment");
        var other=course.basket(world,1);
        var wrong=MathUtil.trace(round,other.clone().add(-3,0,0),new Vector(6,0,0));
        check(wrong==null || !wrong.goal(),"other basket does not score");
        Location start=tee.clone().add(0,1,0);
        world.getBlockAt(start.clone().add(2,0,0)).setType(Material.OAK_SLAB);
        var slab=MathUtil.trace(round,start,new Vector(4,0,0));
        check(slab!=null && !slab.goal() && slab.location().getX()<start.getX()+3,"non-basket slab blocks shot without scoring");
        world.getBlockAt(start.clone().add(2,0,0)).setType(Material.AIR);
        var queue=manager.getRoundQueueManager();
        queue.addRoundToQueue(round);
        var commit=RoundQueueManager.class.getDeclaredMethod("commitParty",GameRound.class,List.class,String.class); commit.setAccessible(true);
        List<UUID> players=List.of(UUID.randomUUID(),UUID.randomUUID());
        check(commit.invoke(queue,round,players,"ffa")==RoundQueueManager.QueueResult.SUCCESS,"party commit");
        for(UUID player:players) check(queue.isPlayerQueued(player)&&round.containsPlayer(player),"party remains in queue after successful commit");
        check(round.getTeams().size()==2,"FFA party members have independent scorecards");
        var doubles=(RoundImpl)manager.getRoundHandler().createRound("doubles",course,"ndg_smoke_"+UUID.randomUUID(),new DefaultRoundSettings(4,RoundType.RECREATIONAL,Division.JUNIOR,false));
        queue.addRoundToQueue(doubles);
        check(commit.invoke(queue,doubles,players,"doubles")==RoundQueueManager.QueueResult.SUCCESS,"doubles party commit");
        check(doubles.getTeams().size()==1 && doubles.getTeams().getFirst().isFull(),"doubles party stays on one team");
        var startRound=RoundQueueManager.class.getDeclaredMethod("shouldStartRound",GameRound.class);startRound.setAccessible(true);
        check(!(boolean)startRound.invoke(queue,doubles),"one doubles team waits for opposition");
        commit.invoke(queue,doubles,List.of(UUID.randomUUID(),UUID.randomUUID()),"doubles");
        var four = List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        check(commit.invoke(queue,doubles,four,"doubles")==RoundQueueManager.QueueResult.SUCCESS,"four friends queue as two doubles teams");
        check(doubles.getTeams().size()==4 && doubles.getTeams().stream().allMatch(t->t.isFull()),"all doubles teams complete");
        check((boolean)startRound.invoke(queue,doubles),"two complete doubles teams can start");
        checkRules(round, world, false);
        checkRules(doubles, Bukkit.getWorld(doubles.getId()), true);
        finishRound(round,world);
        var limitedTeam=doubles.play().currentTeam();
        var limitedCard=doubles.getScoreCardByTeam(limitedTeam);
        limitedCard.addPenalty(doubles.play().maxStrokes()+5);
        var limit=doubles.play().getClass().getDeclaredMethod("finishHole",normalmanv2.normalDiscGolf.api.team.Team.class,String.class);
        limit.setAccessible(true);limit.invoke(doubles.play(),limitedTeam,"Test timeout");
        check(limitedCard.getHoleScores().get(0)==doubles.play().maxStrokes(),"stroke limit caps penalties and concessions");
        manager.getRoundHandler().discardRound(doubles);queue.removeRoundFromQueue(doubles);
        queue.removeRoundFromQueue(round);manager.getRoundHandler().discardRound(round);
        check(Bukkit.getWorld(name)==null,"discard unloads world");
        check(!Files.exists(Bukkit.getWorldContainer().toPath().resolve(name)),"discard deletes owned world");
        Path zip=manager.getPacked().getBytes().length>0?NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getDataFolder().toPath().resolve("NormalDiscGolfPack.zip"):null;
        check(zip!=null && manager.getPacked().getHash().length==20,"resource pack SHA-1");
        check(Arrays.equals(java.security.MessageDigest.getInstance("SHA-1").digest(manager.getPacked().getBytes()),manager.getPacked().getHash()),"resource pack hash matches bytes");
        var delivered=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(manager.getPacked().getPackUrl())).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        check(delivered.statusCode()==200 && Arrays.equals(delivered.body(),manager.getPacked().getBytes()),"HTTP resource pack delivery");
        try(ZipFile pack=new ZipFile(zip.toFile())) {
            for(String type:List.of("putter","midrange","driver")) check(pack.getEntry("assets/ndg/items/"+type+".json")!=null,"pack contains "+type);
            for(var entries=pack.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();
                try(var reader=new java.io.InputStreamReader(pack.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                    check(com.google.gson.JsonParser.parseReader(reader).isJsonObject(),"valid JSON: "+entry.getName());
                }
            }
        }
    }
    private void checkRules(RoundImpl round, World world, boolean doubles) {
        round.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        for(var team:round.getTeams()) round.getRoundScoreCardManager().addScoreCard(team,new normalmanv2.normalDiscGolf.impl.player.score.ScoreCard());
        round.selectRandomTeamTurn();
        round.play().startHole();
        var team=round.play().currentTeam();
        UUID selected=round.play().thrower(team);
        var location=new java.util.concurrent.atomic.AtomicReference<>(round.getCourse().tee(world,0));
        org.bukkit.entity.Player player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)-> {
            return switch(method.getName()) {
                case "getUniqueId" -> selected;
                case "getWorld" -> world;
                case "getLocation" -> location.get().clone();
                case "getName","getDisplayName" -> "SmokePlayer";
                case "isOnline" -> true;
                case "sendMessage" -> null;
                case "hashCode" -> selected.hashCode();
                case "equals" -> proxy==args[0];
                default -> method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
            };
        });
        check(round.play().validate(player)==null,"selected thrower can throw from lie");
        location.set(location.get().clone().add(10,0,0));
        check(round.play().validate(player)!=null,"walking away from lie rejects throw");
        location.set(round.getCourse().tee(world,0));
        check(round.play().beginShot(player),"shot accepted at lie");
        check(!round.play().beginShot(player),"overlapping shot rejected");
        round.getScoreCardByTeam(team).trackStroke();
        if(doubles) {
            round.play().resolve(player,location.get(),false);
            check(!selected.equals(round.play().thrower(team)),"doubles alternates thrower after resolution");
        } else {
            round.play().resolve(player,round.getCourse().basket(world,0),true);
            check(round.getScoreCardByTeam(team).getHoleScores().get(0)==1,"goal records one stroke");
            check(!round.isTurn(team),"scoring team yields turn");
            round.play().resolve(player,round.getCourse().basket(world,0),true);
            check(round.getScoreCardByTeam(team).getTotalStrokes()==1,"stale flight cannot score twice");
        }
    }

    private void finishRound(RoundImpl round, World world) {
        int limit=round.getCourse().holes()*round.getTeams().size()+2;
        while(!round.isOver() && limit-->0) {
            var team=round.play().currentTeam(); UUID id=round.play().thrower(team);
            Location lie=round.play().lie(team);
            org.bukkit.entity.Player player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> lie.clone();
                case "getName","getDisplayName" -> "SmokePlayer";
                case "isOnline" -> true;
                case "sendMessage" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy==args[0];
                default -> method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
            });
            check(round.play().beginShot(player),"next hole accepts throw");
            round.getScoreCardByTeam(team).trackStroke();
            round.play().resolve(player,round.getCourse().basket(world,round.getCurrentHole()),true);
        }
        check(round.getState()==normalmanv2.normalDiscGolf.impl.round.RoundState.END,"final hole reaches END");
        for(var card:round.getScoreCards().values()) check(card.getHoleScores().size()==round.getCourse().holes(),"every team completed every hole");
    }

    private static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name); }
}
