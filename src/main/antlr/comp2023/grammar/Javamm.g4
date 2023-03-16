grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : ('-')?[0-9]+ ;
ID : [a-zA-Z_][a-zA-Z_0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program : (importDeclaration)* (classDeclaration)+ (mainMethodDeclaration)? EOF ;

// import declaration that allows empty string
importDeclaration : 'import' name+=ID ('.' name+=ID)* ';' ;

classDeclaration : 'class' className=ID ( 'extends' extendName=ID )? '{' ( varDeclaration )* ( methodDeclaration )* (mainMethodDeclaration)*'}' ;

varDeclaration : type name=ID ';' ;

type : name = 'int' '[' ']' #array
    | name = 'boolean' #boolean
    | name = 'int' #int
    | name = 'String' #string
    | name = ID #class
    ;

parameter : type name=ID ;

mainMethodDeclaration : ('public')? 'static' 'void' 'main' '(' 'String' '[' ']' name=ID ')' '{' varDeclaration? statement? '}' ;

methodDeclaration :
    ('public' | 'private' | 'protected')? type name=ID '(' ( parameter ( ',' parameter )* )? ')' '{' ( varDeclaration )* ( statement )* returnStatement? '}' ;

returnStatement : 'return' expression ';' ;

statement :
    'if' '(' expression ')' '{' ( statement )* returnStatement? '}' ('else if' '(' expression ')' '{' ( statement )* returnStatement? '}')* ( 'else' '{' ( statement )* returnStatement? '}' )?
    | 'if' '(' expression ')' ( statement ) ( 'else' statement )?
    | 'while' '(' expression ')' '{' ( statement )* '}'
    | 'while' '(' expression ')' ( statement )
    | 'System.out.println' '(' expression ')' ';'
    | ID '=' expression ';'
    | expression '[' expression ']' '=' expression ';'
    | expression '.' 'length' '=' expression ';'
    | expression '.' ID '(' ( expression ( ',' expression )* )? ')' ';'
    | expression ';'
    | '{' ( statement )* '}'
    ;

expression
    : expression op = ('*' | '/') expression
    | expression op = ('+' | '-') expression
    | expression op = ('<' | '>' | '>=' | '<=' | '&&' | '||') expression
    | expression '[' expression ']'
    | expression '.' 'length'
    | expression '.' ID '(' ( expression ( ',' expression )* )? ')'
    | 'new' 'int' '[' expression ']'
    | 'new' ID '(' ')'
    | '!' expression
    | '(' expression ')'
    | value=INTEGER
    | value=ID
    | 'true'
    | 'false'
    | 'this'
    ;

