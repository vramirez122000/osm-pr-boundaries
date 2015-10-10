CREATE TABLE remove_cayos AS
  SELECT
    gid,
    muni_left                                              AS "left:municipio",
    muni_right                                             AS "right:municipio",
    barrio_lef                                             AS "left:barrio",
    barrio_rig                                             AS "right:barrio",
    'administrative' :: VARCHAR(50)                        AS boundary,
    (CASE WHEN costa IS NOT NULL THEN 'coastline'
     ELSE costa END)                                       AS "natural",
    (CASE WHEN muni_left <> muni_right OR costa IS NOT NULL THEN 6
     ELSE 8 END)                                              admin_level,
    'Junta de Planificacion de Puerto Rico' :: VARCHAR(50) AS source,
    'Puerto Rico Planning Board' :: VARCHAR(50)            AS "source:en",
    geom
  FROM jp_admin_pr
  WHERE st_isclosed(geom) = FALSE OR
        (st_isclosed(geom) AND st_area(st_transform(st_makePolygon(geom), 32161)) > 30000);


CREATE TABLE remove_culebra_cayos AS
  SELECT
    *
  FROM remove_cayos
  WHERE "left:municipio" || "left:barrio" <> 'CulebraIslotes y Cayos'
  ORDER BY "left:municipio", "right:municipio", "left:barrio", "right:barrio";


CREATE TABLE merged_ways AS
  SELECT
    "left:municipio",
    "right:municipio",
    "left:barrio",
    "right:barrio",
    "natural",
    source,
    "source:en",
    boundary,
    admin_level,
    (st_dump(st_linemerge(st_union(geom)))).geom AS geom
  FROM remove_culebra_cayos
  GROUP BY "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary,
    admin_level
  ORDER BY "left:municipio", "right:municipio", "left:barrio", "right:barrio";

create table splitted_ways as
  select "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary, admin_level, (st_dump(st_split(geom, st_pointn(geom, 2000)))).geom geom
  from merged_ways
  where st_npoints(geom) > 2000
  union select * from merged_ways where st_npoints(geom) <=2000;

create table splitted_ways2 as
  select "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary, admin_level, (st_dump(st_split(geom, st_pointn(geom, 2000)))).geom geom
  from splitted_ways
  where st_npoints(geom) > 2000
  union select * from splitted_ways where st_npoints(geom) <=2000;

create table splitted_ways3 as
  select "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary, admin_level, (st_dump(st_split(geom, st_pointn(geom, 2000)))).geom geom
  from splitted_ways2
  where st_npoints(geom) > 2000
  union select * from splitted_ways2 where st_npoints(geom) <=2000;

create table splitted_ways4 as
  select "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary, admin_level, (st_dump(st_split(geom, st_pointn(geom, 2000)))).geom geom
  from splitted_ways3
  where st_npoints(geom) > 2000
  union select * from splitted_ways3 where st_npoints(geom) <=2000;

create table splitted_ways5 as
  select "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary, admin_level, (st_dump(st_split(geom, st_pointn(geom, 2000)))).geom geom
  from splitted_ways4
  where st_npoints(geom) > 2000
  union select * from splitted_ways4 where st_npoints(geom) <=2000;

create table splitted_ways6 as
  select "left:municipio", "right:municipio", "left:barrio", "right:barrio", "natural", source, "source:en", boundary, admin_level, (st_dump(st_split(geom, st_pointn(geom, 2000)))).geom geom
  from splitted_ways5
  where st_npoints(geom) > 2000
  union select * from splitted_ways5 where st_npoints(geom) <=2000;

create table final_splitted_ways as
select row_number() OVER (ORDER BY geom) AS gid, splitted_ways6.* from splitted_ways6;

create sequence relation_id_seq;

create table if not exists relations (
  relation_id INT          NOT NULL,
  municipio   VARCHAR(100) NOT NULL,
  barrio      VARCHAR(100),
  admin_level INT          NOT NULL,
  way_gid     BIGINT       NOT NULL
);








