
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order' .. voucherId

if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1; -- 库存
end

if(redis.call('sismember', orderKey, userId) == 1) then
    return 2; -- 重复下单
end

redis.call('incrby', stockKey, -1) -- 减库存
redis.call('sadd', orderKey, userId) -- 保存用户
return 0

