if redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return {'NOT_FOUND'} end
return {'OK', redis.call('pttl', KEYS[1]), redis.call('hget', KEYS[1], 'data')}
