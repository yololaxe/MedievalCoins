package fr.renblood.medievalcoins.land;
public record LandMapLayout(double scale,int x,int y,int width,int height,int mapX,int mapY,int cell,int infoX,int infoWidth) {
    public static LandMapLayout fit(int width,int height,int columns,int rows){double scale=Math.min(1,Math.min(width/660.0,height/420.0));int vw=(int)(width/scale),vh=(int)(height/scale),w=Math.min(960,vw-24),h=Math.min(560,vh-24),x=(vw-w)/2,y=(vh-h)/2;int available=w*3/5-32;int cell=Math.max(1,Math.min(available/columns,(h-156)/rows));return new LandMapLayout(scale,x,y,w,h,x+18+(available-columns*cell)/2,y+90+(h-156-rows*cell)/2,cell,x+w*3/5+12,w-w*3/5-30);}
}
