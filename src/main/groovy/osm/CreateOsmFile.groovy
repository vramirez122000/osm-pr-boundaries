package osm

import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import org.postgis.LineString
import org.postgis.PGgeometry
import org.postgis.Point

import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
import java.text.DecimalFormat

/**
 * Created by victor on 11/2/14.
 */
def db = [
        url     : 'jdbc:postgresql_postGIS:jp_admin_boundaries',
        user    : 'postgres',
        password: '',
        driver  : 'org.postgis.DriverWrapper'
]

def sql = Sql.newInstance(db.url, db.user, db.password, db.driver)
sql.enableNamedQueries = false

sql.execute('truncate table "relations"')
int relationId = 1

sql.eachRow('''
    select distinct * from (
    select "left:municipio" municipio from final_splitted_ways
    union select "right:municipio" municipio from final_splitted_ways) subview;
    ''') { GroovyResultSet row ->

    String municipio = row.municipio

    String sqlStr = """
            insert into relations(relation_id, municipio, barrio, way_gid, admin_level)
            select ${relationId++} relation_id, '${municipio}' municipio, null as barrio, gid as way_gid, 6 as admin_level
            from final_splitted_ways
            where ("left:municipio" = '${municipio}' or "right:municipio" = '${municipio}')
            and ("left:municipio" <> "right:municipio" or "natural" = 'coastline')
        """
    sql.execute(sqlStr)

    sql.eachRow("""
        select distinct * from (
        select "left:barrio" barrio from final_splitted_ways
            where "left:municipio" = '${municipio}' or "right:municipio" = '${municipio}'
        union select "right:barrio" barrio from final_splitted_ways
            where "left:municipio" = '${municipio}' or "right:municipio" = '${municipio}') subview;
        """) { GroovyResultSet barrioRow ->
        String barrio = barrioRow.barrio

        sqlStr = """
            insert into relations(relation_id, municipio, barrio, way_gid, admin_level)
            select ${relationId++} relation_id, '${municipio}' municipio, '${barrio}' as barrio, gid as way_gid, 8 as admin_level
            from final_splitted_ways
            where ("left:municipio" = '${municipio}' or "right:municipio" = '${municipio}')
            and ("left:barrio" = '${barrio}' or "right:barrio" = '${barrio}')
            """
        sql.execute(sqlStr)
    }
}

int nodeIdSeq = -10000
int relationIdSeq = -200000

DecimalFormat coordsFormat = new DecimalFormat("#.0000000");


XMLOutputFactory factory = XMLOutputFactory.newInstance();
XMLStreamWriter out = factory.createXMLStreamWriter(new OutputStreamWriter(new FileOutputStream(new File('boundaries.osm')), 'UTF-8'))
out.writeStartDocument('UTF-8', '1.0');
out.writeStartElement("osm");
out.writeAttribute('version', '0.6')
out.writeAttribute('upload', 'false')
out.writeAttribute('generator', 'StAX')

sql.eachRow('select * from final_splitted_ways') { GroovyResultSet wayRow ->
    PGgeometry geom = wayRow.geom
    LineString lineString = (LineString) geom.getGeometry()
    Point[] points = lineString.getPoints()
    def nodeIds = []
    for (Point p : points) {
        nodeIds << nodeIdSeq

        out.writeStartElement("node");
        out.writeAttribute('id', String.valueOf(nodeIdSeq--))
        out.writeAttribute('visible', 'true')
        out.writeAttribute('lat', coordsFormat.format(p.y))
        out.writeAttribute('lon', coordsFormat.format(p.x))
        out.writeEndElement()
    }

    out.writeStartElement('way')
    out.writeAttribute('id', String.valueOf(-(wayRow.gid)))
    out.writeAttribute('visible', 'true')
    for (int nodeId : nodeIds) {
        out.writeStartElement('nd')
        out.writeAttribute('ref', String.valueOf(nodeId))
        out.writeEndElement()
    }
    writeTag(out, 'boundary', 'administrative')
    writeTag(out, 'admin_level', wayRow.admin_level)
    writeTag(out, 'source', 'Junta de Planificación de Puerto Rico')
    writeTag(out, 'source:en', 'Puerto Rico Planning Board')
    out.writeEndElement() //way
}

Map<String, Object> prevRelation
List<Integer> wayIds = []
sql.eachRow('select * from relations order by relation_id') { GroovyResultSet relRow ->
    if(prevRelation == null) {
        prevRelation = relRow.toRowResult()
        wayIds << (Integer) relRow.way_gid
    } else if (prevRelation.relation_id == relRow.relation_id){
        wayIds << (Integer) relRow.way_gid
    } else {
        Integer number = relationIdSeq - (Integer) prevRelation.relation_id
        out.writeStartElement('relation')
        out.writeAttribute('id', String.valueOf(number))
        out.writeAttribute('visible', 'true')

        for (Integer wayId : wayIds) {
            out.writeStartElement('member')
            out.writeAttribute('type', 'way')
            out.writeAttribute('ref', String.valueOf(-wayId))
            out.writeAttribute('role', 'outer')
            out.writeEndElement()
        }

        writeTag(out, 'type', 'boundary')
        writeTag(out, 'admin_level', prevRelation.admin_level)
        writeTag(out, 'name', prevRelation.admin_level == 6 ? prevRelation.municipio : prevRelation.barrio)
        writeTag(out, 'source', 'Junta de Planificación de Puerto Rico')
        writeTag(out, 'source:en', 'Puerto Rico Planning Board')

        out.writeEndElement() //relation
        prevRelation = relRow.toRowResult()
        wayIds.clear()
        wayIds << (Integer) relRow.way_gid
    }
}

out.writeEndElement() //osm
out.close()

def writeTag(XMLStreamWriter out, String key, Object val) {
    out.writeStartElement('tag')
    out.writeAttribute('k', key)
    out.writeAttribute('v', String.valueOf(val))
    out.writeEndElement()
}
