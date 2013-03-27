---
title: "CFPB - Qu: Data Publishing"
layout: article
---

## Dataset publishing format

The format datasets are published and loaded in is highly influenced by [Google's Dataset Publishing Language](https://developers.google.com/public-data/overview) (DSPL). DSPL uses XML to define a dataset and CSV to hold the data; we instead use JSON and CSV, and we use a subset of the definition that Google does, but otherwise the format is the same. Note that you can include any information inside the dataset definition as long as it is valid JSON. This data could be used later by new features.

Here is an example, broken up into sections: ([see the full definition](https://github.cfpb.gov/cndreisbach/data-api/blob/master/resources/datasets/census/definition.json))

```json
  "info": {
    "name": "Tax Year 2007 County Income Data",
    "description": "Contains selected individual income tax return data items classified by state and county.",
    "url": "https://explore.data.gov/Population/Tax-Year-2007-County-Income-Data/wvps-imhx"
  }
```

Here we specify general information about the dataset: the name and description we want to display for it, and the URL you can get more information from. 

The `info` section is **required**. Within this section, `name` is **required**, while `description` and `url` are optional, but _recommended_.

```json
  "concepts": {
    "state_abbr": {
      "description": "State Abbreviation"
    },
    "county": {
      "description": "County"
    },
    "tax_returns": {
      "description": "Total Number of Tax Returns"
    },
    "adjusted_gross_income": {
      "description": "Adjusted Gross Income (In Thousands)"
    },
    "wages_and_salaries_income": {
      "description": "Wages and Salaries (In Thousands)"
    },
    "dividend_income": {
      "description": "Dividend Income (In Thousands)"
    },
    "interest_income": {
      "description": "Interest Income (In Thousands)"
    }
  }
```

For now, concepts are nice, but not that full-featured. Within each concept, you can specify information about that concept. `description` will be displayed in place of the field name within the user interface.

The `concepts` section is _recommended_, as is `description` within each concept.

```json
  "slices": {
    "incomes": {
      "table": "incomes",
      "dimensions": [
        "state_abbr", "county"
      ],
      "metrics": [
        "tax_returns",
        "adjusted_gross_income",
        "wages_and_salaries_income",
        "dividend_income",
        "interest_income"
      ]
    }
  }
```

_Slices_ are sections of the dataset. (Google refers to them as "a combination of concepts for which data exist.") Each slice is backed by a database table, specified by `table`. Within each section, there are _dimensions_, which are concepts used to filter and query your data, and _metrics_, observed values associated with a data point.

The `slices` section is **required**. Within each slice, `table`, `dimensions`, and `metrics` are **required**.

```json
  "tables": {
    "incomes": {
      "sources": [
        "Tax_Year_2007_County_Income_Data.csv"
      ],
      "columns": {
        "County Code": {
          "skip": true
        },
        "State Abbreviation": {
          "name": "state_abbr",
          "type": "string"
        },
        "County Name": {
          "name": "county",
          "type": "string"
        },
        "Total Number of Tax Returns": {
          "name": "tax_returns",
          "type": "integer"
        },
        "Total Number of Exemptions": {
          "name": "exceptions",
          "type": "integer"
        },
        "Adjusted Gross Income (In Thousands)": {
          "name": "adjusted_gross_income",
          "type": "dollars"
        }
        // ...
      }
    }
  }
```

Tables are storage for a dataset. Each table has one or more sources, which are CSV files. Each table also has one or more columns, which define the transformation of data from the CSV files to the database. Within the `columns` section, each key is the name of a column in the CSV file. The dictionary associated with each column contains a name, which is what the concept name for that column will be (and is the name used when storing the data) and a type, which tells the importer how to translate the information in the CSV, which is all strings, into a data value. The column can also contain `skip`, which if true, tells the importer to throw away that column.

The `tables` section is **required**. Within each table, the `sources` and `columns` sections are **required**. Within each column, `name` and `type` are _recommended_ if `skip` is not true. If `name` is left out, the column name will be used. If `type` is left out, `string` will be used. Current supported types are:

* string
* integer
* dollars (strips off leading dollar sign and converts to an integer)
* lookup (looks up the value in a dictionary provided, see https://github.com/cfpb/qu/blob/master/resources/datasets/census/definition.json#L53 for more details)
