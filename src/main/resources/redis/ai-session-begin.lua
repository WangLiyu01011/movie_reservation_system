if redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return {'NOT_FOUND'} end
if not redis.call('set', KEYS[2], ARGV[2], 'NX', 'PX', ARGV[3]) then return {'BUSY'} end
redis.call('pexpire', KEYS[1], ARGV[4])
return {'OK', redis.call('hget', KEYS[1], 'version'), redis.call('hget', KEYS[1], 'data')}
