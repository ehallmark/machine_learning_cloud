CREATE OR REPLACE FUNCTION word_array(t text) 
RETURNS varchar[] AS 
$$ BEGIN
    RETURN ARRAY_AGG(regexp_replace(coalesce(lower(temp.result),''), '[^a-z]', '', 'g')) FROM (SELECT * from UNNEST(STRING_TO_ARRAY(regexp_replace(t, '\\p{P}', ' ', 'g'),' ')) as result where char_length(regexp_replace(result, '[^a-z]', '', 'g')) > 0) as temp;
   END
$$ LANGUAGE plpgsql;
