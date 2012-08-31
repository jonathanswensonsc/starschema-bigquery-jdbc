/**
 *  Starschema Big Query JDBC Driver
 *  Copyright (C) 2012, Starschema Ltd.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 * This class implements the java.sql.Resultset interface
 */
package net.starschema.clouddb.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import com.google.api.services.bigquery.model.GetQueryResultsResponse;
import com.google.api.services.bigquery.model.TableRow;

/**
 * This class implements the java.sql.ResultSet interface its superclass is
 * ScrollableResultset
 * 
 * @author Horv�th Attila
 */
public class BQResultSet extends ScrollableResultset<Object> implements
	java.sql.ResultSet {

    /**
     * This Reference is for storing the GetQueryResultsResponse got from
     * bigquery
     */
    private GetQueryResultsResponse Result = null;
    /**
     * This Reference is for storing the Reference for the Statement which
     * created this Resultset
     */
    private BQStatement Statementreference = null;

    /**
     * Constructor of BQResultset, that initializes all private variables
     * 
     * @param BigQueryGetQueryResultResponse
     *            BigQueryGetQueryResultResponse from Bigquery
     * @param bqStatement
     *            Reference of the Statement that creates this Resultset
     */
    public BQResultSet(GetQueryResultsResponse BigQueryGetQueryResultResponse,
	    BQStatement bqStatement) {

	this.Result = BigQueryGetQueryResultResponse;
	if (this.Result.getRows() == null)
	    this.RowsofResult = null;
	else
	    this.RowsofResult = this.Result.getRows().toArray();
	this.Statementreference = bqStatement;
    }

    /** {@inheritDoc} */
    @Override
    public int findColumn(String columnLabel) throws SQLException {
	if (this.isClosed())
	    throw new SQLException("This Resultset is Closed");
	int columncount = this.getMetaData().getColumnCount();
	for (int i = 1; i <= columncount; i++)
	    if (this.getMetaData().getCatalogName(i).equals(columnLabel))
		return i;
	SQLException e = new SQLException("No Such column labeled: "
		+ columnLabel);
	throw e;
    }

    /** {@inheritDoc} */
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
	if (this.isClosed())
	    throw new SQLException("This Resultset is Closed");
	return new BQResultsetMetaData(this.Result);
    }

    /** {@inheritDoc} */
    @Override
    public Object getObject(int columnIndex) throws SQLException {
	this.closestrm();
	if (this.isClosed())
	    throw new SQLException("This Resultset is Closed");
	this.ThrowCursorNotValidExeption();
	if (this.RowsofResult == null)
	    throw new SQLException("There are no rows in this Resultset");
	if (this.getMetaData().getColumnCount() < columnIndex
		|| columnIndex < 1)
	    throw new SQLException("ColumnIndex is not valid");
	String Columntype = this.Result.getSchema().getFields()
		.get(columnIndex - 1).getType();
	String result = ((TableRow) this.RowsofResult[this.Cursor]).getF()
		.get(columnIndex - 1).getV();
	if (result == null) {
	    this.wasnull = true;
	    return null;
	} else {
	    this.wasnull = false;
	    try {
		if (Columntype.equals("FLOAT"))
		    return Float.parseFloat(result);
		else if (Columntype.equals("BOOLEAN"))
		    return Boolean.parseBoolean(result);
		else if (Columntype.equals("INTEGER"))
		    return Integer.parseInt(result);
		else if (Columntype.equals("STRING"))
		    return (result);
		else
		    throw new SQLException("Unsupported Type");
	    } catch (NumberFormatException e) {
		throw new SQLException(e);
	    }
	}
    }

    /** {@inheritDoc} */
    @Override
    public Statement getStatement() throws SQLException {
	return this.Statementreference;
    }

    /** {@inheritDoc} */
    @Override
    public String getString(int columnIndex) throws SQLException {
	this.closestrm();
	this.ThrowCursorNotValidExeption();
	if (this.isClosed())
	    throw new SQLException("This Resultset is Closed");
	String result = ((TableRow) this.RowsofResult[this.Cursor]).getF()
		.get(columnIndex - 1).getV();
	if (result == null)
	    this.wasnull = true;
	else
	    this.wasnull = false;
	return result;
    }
}
