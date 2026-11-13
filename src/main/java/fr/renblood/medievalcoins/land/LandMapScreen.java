package fr.renblood.medievalcoins.land;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** A readable cadastral map; inventory slots are only the server synchronization transport. */
public class LandMapScreen extends AbstractContainerScreen<LandMapMenu> {
    private static final int INK=0xFF101D25,PANEL=0xFF172B32,GOLD=0xFFE2BE77,PAPER=0xFFECE5D5,MUTED=0xFFABB8B7;
    private boolean showTerrain=true;private Button terrainButton;
    private LandMapLayout layout;private int selected;private String query="",target="",filteredQuery;private java.util.List<LandMapMenu.PlotChoice> filteredChoices=java.util.List.of();private EditBox search;private Button choose,assign,release,ownerButton;private LandMapAction pending;
    public LandMapScreen(LandMapMenu menu,Inventory inventory,Component title){super(menu,inventory,title);selected=menu.getColumns()/2+(menu.getRows()/2)*menu.getColumns();inventoryLabelY=10000;}
    @Override protected void init(){super.init();layout=LandMapLayout.fit(width,height,menu.getColumns(),menu.getRows());imageWidth=(int)Math.ceil(layout.width()*layout.scale());imageHeight=(int)Math.ceil(layout.height()*layout.scale());leftPos=(int)(layout.x()*layout.scale());topPos=(int)(layout.y()*layout.scale());clearWidgets();choose=assign=release=null;
        int x=layout.x(),y=layout.y(),w=layout.width(),h=layout.height(),rx=layout.infoX(),rw=layout.infoWidth();
        int bx=x+18;String[] labels={"Ouest","Nord","Sud","Est","Ici","Actualiser"};int[][] moves={{-1,0},{0,-1},{0,1},{1,0},{99,0},{0,0}};
        for(int i=0;i<labels.length;i++){final int at=i;int bw=i==5?70:42;flat(labels[i],bx,y+64,bw,()->{pending=null;selected=menu.getColumns()/2+(menu.getRows()/2)*menu.getColumns();send(iAction(at),moves[at][0],moves[at][1],"","");});bx+=bw+4;}
        flat("Limites de mon chunk · 20 s",x+18,y+h-33,200,()->{if(minecraft.player!=null){var pos=minecraft.player.chunkPosition();int py=minecraft.player.blockPosition().getY();fr.renblood.medievalcoins.client.renderer.RegionRenderer.addHighlight(new net.minecraft.core.BlockPos(pos.getMinBlockX(),py,pos.getMinBlockZ()),new net.minecraft.core.BlockPos(pos.getMinBlockX()+15,py+3,pos.getMinBlockZ()+15),0xDDE2BE77,400);}onClose();});
        terrainButton=flat(showTerrain?"Fond : Minecraft":"Fond : grille",x+230,y+h-33,140,()->{showTerrain=!showTerrain;terrainButton.setMessage(Component.literal(showTerrain?"Fond : Minecraft":"Fond : grille"));});
        flat("Fermer",x+w-88,y+h-33,70,this::onClose);
        if(menu.isAdministrator()){
            search=addRenderableWidget(new EditBox(font,rx,y+h-146,rw,18,Component.literal("Rechercher une parcelle")));search.setMaxLength(128);search.setValue(query);search.setHint(Component.literal("Nom ou identifiant…"));search.setResponder(value->{query=value;pending=null;});
            choose=flat("Parcelle cible",rx,y+h-120,rw,()->{var choices=filtered();if(choices.isEmpty())return;int next=0;for(int i=0;i<choices.size();i++)if(choices.get(i).id().equals(target))next=(i+1)%choices.size();target=choices.get(next).id();pending=null;});
            assign=flat("Attribuer",rx,y+h-91,(rw-6)/2,()->prepare(2));release=flat("Retirer",rx+(rw+6)/2,y+h-91,(rw-6)/2,()->{if(pending!=null)pending=null;else prepare(3);});
            flat("Créer…",rx,y+h-62,(rw-6)/2,()->{onClose();minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/mc land create "));});
            ownerButton=flat("Propriétaire…",rx+(rw+6)/2,y+h-62,(rw-6)/2,()->{var cell=selected();String id=cell.getString("id");if(id.isBlank())return;String type=cell.getString("type");if(type.equals("UNKNOWN")||type.isBlank())type="PLAYER";String command="/mc land set-owner "+id+" "+type+" "+cell.getString("owner");onClose();minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen(command));});
        }
    }
    private int iAction(int index){return index==5?1:0;}
    private java.util.List<LandMapMenu.PlotChoice> filtered(){if(!query.equals(filteredQuery)){filteredQuery=query;String q=query.toLowerCase(Locale.ROOT);filteredChoices=menu.choices().stream().filter(p->(p.name()+" "+p.id()).toLowerCase(Locale.ROOT).contains(q)).toList();}return filteredChoices;}
    private CompoundTag selected(){return menu.cell(selected);}
    private void send(int action,int cx,int cz,String parcel,String expected){MedievalCoin.PACKET_HANDLER.sendToServer(new LandMapAction(menu.containerId,action,cx,cz,parcel,expected));}
    private void prepare(int action){
        if(pending!=null){LandMapAction request=pending;pending=null;MedievalCoin.PACKET_HANDLER.sendToServer(request);return;}
        var cell=selected();if(!cell.contains("x"))return;pending=new LandMapAction(menu.containerId,action,cell.getInt("x"),cell.getInt("z"),target,cell.getString("id"));
    }
    private Button flat(String text,int x,int y,int w,Runnable action){return addRenderableWidget(new Button(x,y,w,20,Component.literal(text),b->action.run(),supplier->supplier.get()){
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){int color=active?(isHoveredOrFocused()?GOLD:PAPER):0xFF677574;g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isHoveredOrFocused()&&active?0xFF29424A:0xFF20343C);g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),active?0xFF927F55:0xFF34464C);String label=font.plainSubstrByWidth(getMessage().getString(),getWidth()-8);g.drawString(font,label,getX()+(getWidth()-font.width(label))/2,getY()+6,color,false);}
    });}
    private int cellAt(double mx,double my){int col=(int)Math.floor((mx-layout.mapX())/layout.cell()),row=(int)Math.floor((my-layout.mapY())/layout.cell());return col>=0&&col<menu.getColumns()&&row>=0&&row<menu.getRows()?col+row*menu.getColumns():-1;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256){onClose();return true;}
        if(search!=null&&search.isFocused()&&key!=258){search.keyPressed(key,scan,modifiers);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){mx/=layout.scale();my/=layout.scale();int cell=cellAt(mx,my);if(button==0&&cell>=0){setFocused(null);selected=cell;pending=null;String id=selected().getString("id");if(!id.isBlank())target=id;return true;}for(var child:children())if(child.mouseClicked(mx,my,button)){setFocused(child);setDragging(true);return true;}return false;}
    @Override public boolean mouseReleased(double mx,double my,int button){setDragging(false);return getFocused()!=null&&getFocused().mouseReleased(mx/layout.scale(),my/layout.scale(),button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return getFocused()!=null&&getFocused().mouseDragged(mx/layout.scale(),my/layout.scale(),button,dx/layout.scale(),dy/layout.scale());}
    @Override public void mouseMoved(double mx,double my){for(var child:children())child.mouseMoved(mx/layout.scale(),my/layout.scale());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){}
    private void text(GuiGraphics g,String value,int x,int y,int max,int color){g.drawString(font,font.plainSubstrByWidth(value,max),x,y,color,false);}
    private void stroke(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);}
    private String idAt(int index){return index>=0&&index<menu.getColumns()*menu.getRows()?menu.cell(index).getString("id"):"";}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){renderBackground(g);int mx=(int)(mouseX/layout.scale()),my=(int)(mouseY/layout.scale());g.pose().pushPose();g.pose().scale((float)layout.scale(),(float)layout.scale(),1);
        int x=layout.x(),y=layout.y(),w=layout.width(),h=layout.height(),rx=layout.infoX(),rw=layout.infoWidth();g.fill(x,y,x+w,y+h,INK);g.fill(x,y,x+w,y+2,GOLD);
        text(g,"LE CADASTRE DU ROYAUME",x+18,y+16,w-36,GOLD);text(g,"Centre : "+menu.centerX()+", "+menu.centerZ()+"  ·  "+menu.dimension()+"  ·  1 case = 16 × 16 blocs",x+18,y+40,w-36,MUTED);
        g.fill(rx-10,y+64,rx+rw+10,y+h-44,PANEL);
        int cols=menu.getColumns(),rows=menu.getRows(),size=layout.cell(),hover=cellAt(mx,my);
        if(showTerrain)LandTerrainRenderer.render(g,layout,menu);
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            int index=col+row*cols,cx=layout.mapX()+col*size,cy=layout.mapY()+row*size;var tag=menu.cell(index);String id=tag.getString("id");boolean owned=!id.isBlank(),known=tag.getBoolean("known");int color=tag.getInt("color");int shade=owned?0xFF000000|(((color>>16&255)*35/100)<<16)|(((color>>8&255)*35/100)<<8)|((color&255)*35/100):0xFF15252D;
            g.fill(cx,cy,cx+size,cy+size,!known?0xFF0B131B:showTerrain?(owned?0x66000000|color:0x18000000):shade);if(!known)text(g,"?",cx+size/2-2,cy+size/2-4,12,MUTED);stroke(g,cx,cy,size,size,0xFF2A3C42);
            if(owned){int edge=0xFF000000|color;if(col==0||!id.equals(idAt(index-1)))g.fill(cx,cy,cx+2,cy+size,edge);if(col==cols-1||!id.equals(idAt(index+1)))g.fill(cx+size-2,cy,cx+size,cy+size,edge);if(row==0||!id.equals(idAt(index-cols)))g.fill(cx,cy,cx+size,cy+2,edge);if(row==rows-1||!id.equals(idAt(index+cols)))g.fill(cx,cy+size-2,cx+size,cy+size,edge);}
            if(minecraft.player!=null&&tag.contains("x")&&tag.getInt("x")==minecraft.player.chunkPosition().x&&tag.getInt("z")==minecraft.player.chunkPosition().z){int px=cx+size/2,py=cy+size/2;g.fill(px-3,py-3,px+4,py+4,PAPER);g.fill(px-1,py-1,px+2,py+2,INK);}
            if(index==hover)stroke(g,cx+2,cy+2,size-4,size-4,MUTED);if(index==selected)stroke(g,cx+1,cy+1,size-2,size-2,GOLD);
        }
        text(g,"N",layout.mapX()+cols*size/2-3,layout.mapY()-12,10,MUTED);text(g,"S",layout.mapX()+cols*size/2-3,layout.mapY()+rows*size+4,10,MUTED);
        text(g,"■ Attribué   □ Libre   ? Inexploré   ▪ Vous",x+18,y+h-65,w*3/5-28,MUTED);
        var tag=selected();boolean ready=tag.contains("x"),owned=!tag.getString("id").isBlank();text(g,!ready?"Chargement…":!tag.getBoolean("known")?"Territoire inexploré":owned?tag.getString("name"):"Terre non attribuée",rx,y+82,rw,PAPER);
        text(g,tag.getBoolean("known")?"PROPRIÉTAIRE":"À DÉCOUVRIR",rx,y+108,rw,GOLD);text(g,!tag.getBoolean("known")?"Explorez les environs en jeu":owned?(tag.getString("owner").isBlank()?"Non renseigné":tag.getString("owner")):"Aucun",rx,y+125,rw,PAPER);
        text(g,owned?LandMapRules.ownerType(tag.getString("type")):"Domaine sans attribution",rx,y+143,rw,MUTED);text(g,"Ville : "+(tag.getString("city").isBlank()?"—":tag.getString("city")),rx,y+164,rw,MUTED);
        text(g,ready?"Chunk "+tag.getInt("x")+", "+tag.getInt("z"):"",rx,y+182,rw,MUTED);
        if(owned){int line=0;for(var value:font.split(Component.literal(tag.getString("description")),rw)){if(line>=2)break;g.drawString(font,value,rx,y+204+line++*11,MUTED,false);}}
        else if(!menu.isAdministrator())text(g,"L’attribution se prépare avec le staff.",rx,y+208,rw,MUTED);
        if(choose!=null){if(target.isBlank()&&owned)target=tag.getString("id");var matches=filtered();if(matches.stream().noneMatch(p->p.id().equals(target)))target=matches.isEmpty()?"":matches.get(0).id();var choice=matches.stream().filter(p->p.id().equals(target)).findFirst();choose.setMessage(Component.literal(choice.map(p->"→ "+p.name()).orElse("Aucune parcelle trouvée")));choose.active=pending==null&&!matches.isEmpty();
            if(pending==null){assign.setMessage(Component.literal("Attribuer"));release.setMessage(Component.literal("Retirer"));assign.active=ready&&!owned&&!target.isBlank();release.active=ready&&owned;}
            else{assign.setMessage(Component.literal("Confirmer"));assign.active=true;release.setMessage(Component.literal("Annuler"));release.active=true;}
            text(g,pending==null?(menu.optionsTruncated()?"LISTE LIMITÉE · commandes disponibles":"PARCELLE CIBLE"):"CONFIRMER · chunk "+pending.x()+", "+pending.z(),rx,y+h-160,rw,pending==null?GOLD:0xFFF0BC82);
        }
        if(ownerButton!=null)ownerButton.active=ready&&owned&&tag.getString("id").matches("[A-Za-z0-9_.+\\-]+");
        for(var widget:renderables)widget.render(g,mx,my,partial);
        g.pose().popPose();
        if(hover>=0){var c=menu.cell(hover);if(c.contains("x"))g.renderTooltip(font,java.util.List.of(Component.literal(!c.getBoolean("known")?"Territoire inexploré":c.getString("id").isBlank()?"Non attribué":c.getString("name")),Component.literal(!c.getBoolean("known")?"Visitez ce secteur pour connaître son propriétaire":c.getString("owner").isBlank()?"Aucun propriétaire indiqué":c.getString("owner"))),Optional.empty(),mouseX,mouseY);}

    }
}
