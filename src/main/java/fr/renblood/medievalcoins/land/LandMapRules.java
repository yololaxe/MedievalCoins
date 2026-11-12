package fr.renblood.medievalcoins.land;
public final class LandMapRules {
    public static boolean visible(int x,int z,int cx,int cz,int columns,int rows){return Math.abs((long)x-cx)<=columns/2&&Math.abs((long)z-cz)<=rows/2;}
    public static boolean nearby(int x,int z,int playerX,int playerZ){return Math.abs((long)x-playerX)<=64&&Math.abs((long)z-playerZ)<=64;}
    public static String ownerType(String type){return switch(type){case "PLAYER"->"Joueur";case "CITY"->"Ville";case "GUILD"->"Guilde";case "FAMILY"->"Maison";case "RELIGIOUS"->"Institution religieuse";case "KINGDOM"->"Royaume";case "PUBLIC"->"Domaine public";case "SERVER"->"Administration";default->"Non précisé";};}
    public static int color(String hex,String type){try{if(hex!=null&&hex.matches("#?[0-9a-fA-F]{6}"))return Integer.parseInt(hex.replace("#",""),16);}catch(NumberFormatException ignored){}return switch(type){case "PLAYER"->0x80B89A;case "CITY"->0x739AB9;case "GUILD"->0xD1AD69;case "FAMILY"->0xB78187;case "RELIGIOUS"->0xA297C0;case "KINGDOM"->0xE2BE77;case "PUBLIC"->0x82ADAC;default->0x8B969F;};}
}
