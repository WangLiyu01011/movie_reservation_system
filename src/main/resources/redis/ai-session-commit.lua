if redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return 'NOT_FOUND' end
if redis.call('get', KEYS[2]) ~= ARGV[2] or redis.call('hget', KEYS[1], 'version') ~= ARGV[3] then
    return 'STALE'
end
redis.call('hset', KEYS[1], 'data', ARGV[4], 'version', tostring(tonumber(ARGV[3]) + 1))
redis.call('pexpire', KEYS[1], ARGV[5])
redis.call('del', KEYS[2])
return 'OK'
